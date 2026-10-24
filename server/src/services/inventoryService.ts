import { v4 as uuid } from "uuid";
import type { AuthUser } from "../auth/jwt.js";
import type { DbClient } from "../db/pool.js";
import { pool } from "../db/pool.js";
import { ApiError } from "../errors.js";
import { formatNaira } from "../money.js";
import * as notifications from "./notificationService.js";

async function assertStoreOwner(user: AuthUser, storeId: string) {
  if (user.role !== "MERCHANT") throw ApiError.forbidden();
  const res = await pool.query(`SELECT merchant_id FROM stores WHERE id = $1`, [storeId]);
  if (!res.rows[0]) throw ApiError.notFound("Store not found");
  if (res.rows[0].merchant_id !== user.id) throw ApiError.forbidden();
}

function mapItem(row: {
  id: string;
  store_id: string;
  product_id: string;
  quantity: number;
  unit_price_kobo: string | number;
  currency: string;
}) {
  const unitPriceKobo = Number(row.unit_price_kobo);
  return {
    id: row.id,
    storeId: row.store_id,
    productId: row.product_id,
    quantity: row.quantity,
    unitPriceKobo,
    unitPrice: formatNaira(unitPriceKobo),
    currency: row.currency,
  };
}

export async function list(user: AuthUser, storeId: string) {
  await assertStoreOwner(user, storeId);
  const res = await pool.query(`SELECT * FROM inventory_items WHERE store_id = $1 ORDER BY updated_at DESC`, [
    storeId,
  ]);
  return res.rows.map(mapItem);
}

export async function upsert(
  user: AuthUser,
  storeId: string,
  input: { productId: string; quantity: number; unitPriceKobo: number },
) {
  await assertStoreOwner(user, storeId);
  if (input.quantity < 0 || input.unitPriceKobo < 0) {
    throw ApiError.badRequest("VALIDATION_FAILED", "quantity and price must be >= 0");
  }
  const existing = await pool.query(
    `SELECT id, quantity FROM inventory_items WHERE store_id = $1 AND product_id = $2`,
    [storeId, input.productId],
  );
  const previousQuantity = existing.rows[0] ? Number(existing.rows[0].quantity) : 0;
  const now = new Date();
  if (existing.rows[0]) {
    await pool.query(
      `UPDATE inventory_items SET quantity=$2, unit_price_kobo=$3, updated_at=$4 WHERE id=$1`,
      [existing.rows[0].id, input.quantity, input.unitPriceKobo, now],
    );
  } else {
    await pool.query(
      `INSERT INTO inventory_items (id, store_id, product_id, quantity, unit_price_kobo, currency, created_at, updated_at)
       VALUES ($1,$2,$3,$4,$5,'NGN',$6,$6)`,
      [uuid(), storeId, input.productId, input.quantity, input.unitPriceKobo, now],
    );
  }
  const res = await pool.query(`SELECT * FROM inventory_items WHERE store_id = $1 AND product_id = $2`, [
    storeId,
    input.productId,
  ]);
  const item = mapItem(res.rows[0]);
  if (input.quantity > 0) {
    void notifications
      .matchInventoryUpdate({
        storeId,
        productId: input.productId,
        quantity: input.quantity,
        unitPriceKobo: input.unitPriceKobo,
        previousQuantity,
      })
      .catch(() => undefined);
  }
  return item;
}

export async function restock(
  user: AuthUser,
  storeId: string,
  input: { productId: string; addQuantity: number; unitPriceKobo?: number | null },
) {
  await assertStoreOwner(user, storeId);
  if (!input.addQuantity || input.addQuantity < 1) {
    throw ApiError.badRequest("VALIDATION_FAILED", "addQuantity must be >= 1");
  }
  const existing = await pool.query(
    `SELECT * FROM inventory_items WHERE store_id = $1 AND product_id = $2`,
    [storeId, input.productId],
  );
  if (!existing.rows[0]) {
    if (input.unitPriceKobo == null) {
      throw ApiError.badRequest("VALIDATION_FAILED", "unitPriceKobo required for new stock line");
    }
    return upsert(user, storeId, {
      productId: input.productId,
      quantity: input.addQuantity,
      unitPriceKobo: input.unitPriceKobo,
    });
  }
  const row = existing.rows[0];
  const qty = row.quantity + input.addQuantity;
  const price = input.unitPriceKobo ?? Number(row.unit_price_kobo);
  return upsert(user, storeId, { productId: input.productId, quantity: qty, unitPriceKobo: price });
}

export async function reserve(client: DbClient, storeId: string, productId: string, qty: number) {
  const res = await client.query(
    `SELECT id, quantity, unit_price_kobo FROM inventory_items WHERE store_id = $1 AND product_id = $2 FOR UPDATE`,
    [storeId, productId],
  );
  const row = res.rows[0];
  if (!row || row.quantity < qty) throw ApiError.conflict("OUT_OF_STOCK", "Not enough stock");
  await client.query(`UPDATE inventory_items SET quantity = quantity - $2, updated_at = NOW() WHERE id = $1`, [
    row.id,
    qty,
  ]);
  return { unitPriceKobo: Number(row.unit_price_kobo), remaining: row.quantity - qty };
}

export async function restore(client: DbClient, storeId: string, productId: string, qty: number) {
  await client.query(
    `UPDATE inventory_items SET quantity = quantity + $3, updated_at = NOW()
     WHERE store_id = $1 AND product_id = $2`,
    [storeId, productId, qty],
  );
}
