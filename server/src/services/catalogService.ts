import { v4 as uuid } from "uuid";
import type { AuthUser } from "../auth/jwt.js";
import { pool } from "../db/pool.js";
import { ApiError } from "../errors.js";
import { mediaUrl } from "./mediaService.js";
import { formatNaira } from "../money.js";

export async function listProducts() {
  const products = await pool.query(
    `SELECT id, sku, name, description, image_media_id FROM products ORDER BY name`,
  );
  const aliases = await pool.query(`SELECT product_id, alias FROM product_aliases`);
  const byProduct = new Map<string, string[]>();
  for (const a of aliases.rows) {
    const list = byProduct.get(a.product_id) ?? [];
    list.push(a.alias);
    byProduct.set(a.product_id, list);
  }
  return products.rows.map((p) => ({
    id: p.id,
    sku: p.sku,
    name: p.name,
    description: p.description,
    aliases: byProduct.get(p.id) ?? [],
    imageMediaId: p.image_media_id,
    imageUrl: mediaUrl(p.image_media_id),
  }));
}

export async function createProduct(
  user: AuthUser,
  input: {
    sku?: string | null;
    name: string;
    description?: string | null;
    aliases?: string[];
    imageMediaId?: string | null;
  },
) {
  if (user.role !== "MERCHANT") throw ApiError.forbidden("Merchants only");
  const name = input.name?.trim();
  if (!name) throw ApiError.badRequest("VALIDATION_FAILED", "Name required");

  let sku = input.sku?.trim() || "";
  if (!sku) {
    sku = `SKU-${Date.now().toString(36).toUpperCase()}-${Math.floor(Math.random() * 900 + 100)}`;
  }
  const id = uuid();
  const now = new Date();
  try {
    await pool.query(
      `INSERT INTO products (id, sku, name, description, image_media_id, created_at, updated_at)
       VALUES ($1,$2,$3,$4,$5,$6,$6)`,
      [id, sku, name, input.description?.trim() || null, input.imageMediaId || null, now],
    );
  } catch {
    throw ApiError.conflict("SKU_TAKEN", "SKU already exists");
  }
  const aliases = [...new Set((input.aliases ?? []).map((a) => a.trim().toLowerCase()).filter(Boolean))];
  for (const alias of aliases) {
    await pool.query(`INSERT INTO product_aliases (id, product_id, alias) VALUES ($1,$2,$3)`, [
      uuid(),
      id,
      alias,
    ]);
  }
  return {
    id,
    sku,
    name,
    description: input.description?.trim() || null,
    aliases,
    imageMediaId: input.imageMediaId || null,
    imageUrl: mediaUrl(input.imageMediaId),
  };
}

export { formatNaira };
