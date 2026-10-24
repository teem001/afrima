import { createHash } from "node:crypto";
import { v4 as uuid } from "uuid";
import { pool, withTx } from "../db/pool.js";
import { ApiError } from "../errors.js";
import { formatNaira } from "../money.js";
import * as inventory from "./inventoryService.js";
import * as payments from "./paymentService.js";
import * as preferences from "./preferenceService.js";
function ledgerHash(parts) {
    return createHash("sha256").update(parts.join("|")).digest("hex");
}
async function loadOrder(orderId) {
    const order = await pool.query(`SELECT * FROM orders WHERE id = $1`, [orderId]);
    if (!order.rows[0])
        throw ApiError.notFound("Order not found");
    const lines = await pool.query(`SELECT * FROM order_lines WHERE order_id = $1`, [orderId]);
    return mapOrder(order.rows[0], lines.rows);
}
function mapOrder(row, lines) {
    const totalKobo = Number(row.total_kobo);
    return {
        id: row.id,
        consumerId: row.consumer_id,
        storeId: row.store_id,
        status: row.status,
        totalKobo,
        total: formatNaira(totalKobo),
        currency: row.currency,
        ledgerHash: row.ledger_hash,
        createdAt: row.created_at,
        updatedAt: row.updated_at,
        items: lines.map((l) => {
            const unitPriceKobo = Number(l.unit_price_kobo);
            return {
                productId: l.product_id,
                quantity: l.quantity,
                unitPriceKobo,
                unitPrice: formatNaira(unitPriceKobo),
            };
        }),
    };
}
export async function place(user, input, emit) {
    if (user.role !== "CONSUMER")
        throw ApiError.forbidden();
    if (!input.items?.length)
        throw ApiError.badRequest("VALIDATION_FAILED", "Items required");
    const store = await pool.query(`SELECT * FROM stores WHERE id = $1 AND active = TRUE`, [input.storeId]);
    if (!store.rows[0])
        throw ApiError.notFound("Store not found");
    const order = await withTx(async (client) => {
        const orderId = uuid();
        let total = 0;
        const lineRows = [];
        for (const item of input.items) {
            if (item.quantity < 1)
                throw ApiError.badRequest("VALIDATION_FAILED", "Invalid quantity");
            const reserved = await inventory.reserve(client, input.storeId, item.productId, item.quantity);
            total += reserved.unitPriceKobo * item.quantity;
            lineRows.push({
                productId: item.productId,
                quantity: item.quantity,
                unitPriceKobo: reserved.unitPriceKobo,
            });
            emit("stock.changed", {
                storeId: input.storeId,
                productId: item.productId,
                quantity: reserved.remaining,
                unitPriceKobo: reserved.unitPriceKobo,
            }, { broadcast: true });
        }
        const hash = ledgerHash([
            orderId,
            user.id,
            input.storeId,
            String(total),
            ...lineRows.map((l) => `${l.productId}:${l.quantity}:${l.unitPriceKobo}`),
        ]);
        await client.query(`INSERT INTO orders (id, consumer_id, store_id, status, total_kobo, currency, ledger_hash, created_at, updated_at)
       VALUES ($1,$2,$3,'PLACED',$4,'NGN',$5,NOW(),NOW())`, [orderId, user.id, input.storeId, total, hash]);
        for (const l of lineRows) {
            await client.query(`INSERT INTO order_lines (id, order_id, product_id, quantity, unit_price_kobo) VALUES ($1,$2,$3,$4,$5)`, [uuid(), orderId, l.productId, l.quantity, l.unitPriceKobo]);
        }
        await payments.hold(client, user.id, store.rows[0].merchant_id, input.storeId, orderId, total);
        return orderId;
    });
    const view = await loadOrder(order);
    emit("order.placed", view, { userIds: [user.id, store.rows[0].merchant_id] });
    for (const line of view.items) {
        void pool
            .query(`SELECT category_id FROM products WHERE id = $1`, [line.productId])
            .then((r) => preferences.trackEvent(user.id, {
            type: "ORDER",
            productId: line.productId,
            storeId: view.storeId,
            categoryId: r.rows[0]?.category_id ?? null,
            unitPriceKobo: line.unitPriceKobo,
            quantity: line.quantity,
            payload: { orderId: view.id },
        }))
            .catch(() => undefined);
    }
    return view;
}
export async function mine(user) {
    if (user.role !== "CONSUMER")
        throw ApiError.forbidden();
    const orders = await pool.query(`SELECT * FROM orders WHERE consumer_id = $1 ORDER BY created_at DESC LIMIT 100`, [user.id]);
    return Promise.all(orders.rows.map(async (o) => {
        const lines = await pool.query(`SELECT * FROM order_lines WHERE order_id = $1`, [o.id]);
        return mapOrder(o, lines.rows);
    }));
}
export async function merchantOrders(user) {
    if (user.role !== "MERCHANT")
        throw ApiError.forbidden();
    const orders = await pool.query(`SELECT o.* FROM orders o
     JOIN stores s ON s.id = o.store_id
     WHERE s.merchant_id = $1
     ORDER BY o.created_at DESC LIMIT 100`, [user.id]);
    return Promise.all(orders.rows.map(async (o) => {
        const lines = await pool.query(`SELECT * FROM order_lines WHERE order_id = $1`, [o.id]);
        return mapOrder(o, lines.rows);
    }));
}
export async function get(user, orderId) {
    const view = await loadOrder(orderId);
    if (user) {
        if (user.role === "CONSUMER" && view.consumerId !== user.id)
            throw ApiError.forbidden();
        if (user.role === "MERCHANT") {
            const s = await pool.query(`SELECT merchant_id FROM stores WHERE id = $1`, [view.storeId]);
            if (s.rows[0]?.merchant_id !== user.id)
                throw ApiError.forbidden();
        }
    }
    return view;
}
async function assertMerchantOrder(user, orderId) {
    if (user.role !== "MERCHANT")
        throw ApiError.forbidden();
    const res = await pool.query(`SELECT o.*, s.merchant_id FROM orders o JOIN stores s ON s.id = o.store_id WHERE o.id = $1`, [orderId]);
    if (!res.rows[0])
        throw ApiError.notFound("Order not found");
    if (res.rows[0].merchant_id !== user.id)
        throw ApiError.forbidden();
    return res.rows[0];
}
export async function accept(user, orderId, emit) {
    const row = await assertMerchantOrder(user, orderId);
    if (row.status !== "PLACED")
        throw ApiError.conflict("INVALID_STATUS", "Order cannot be accepted");
    await pool.query(`UPDATE orders SET status = 'ACCEPTED', updated_at = NOW() WHERE id = $1`, [orderId]);
    const view = await loadOrder(orderId);
    emit("order.updated", view, { userIds: [view.consumerId, user.id] });
    return view;
}
export async function fulfil(user, orderId, emit) {
    const row = await assertMerchantOrder(user, orderId);
    if (row.status !== "ACCEPTED" && row.status !== "PLACED") {
        throw ApiError.conflict("INVALID_STATUS", "Order cannot be fulfilled");
    }
    await withTx(async (client) => {
        await client.query(`UPDATE orders SET status = 'FULFILLED', updated_at = NOW() WHERE id = $1`, [orderId]);
        await payments.release(client, orderId);
        // Simulated merkle anchor
        const batchId = uuid();
        const root = ledgerHash([orderId, row.ledger_hash, String(Date.now())]);
        await client.query(`INSERT INTO anchor_batches (id, merkle_root, tx_reference, order_count, status, created_at, updated_at)
       VALUES ($1,$2,$3,1,'SIMULATED',NOW(),NOW())`, [batchId, root, `SIM-${batchId.slice(0, 8)}`]);
        await client.query(`INSERT INTO anchor_items (id, batch_id, order_id, ledger_hash, leaf_index, created_at, updated_at)
       VALUES ($1,$2,$3,$4,0,NOW(),NOW())
       ON CONFLICT (order_id) DO NOTHING`, [uuid(), batchId, orderId, row.ledger_hash]);
    });
    const view = await loadOrder(orderId);
    emit("order.updated", view, { userIds: [view.consumerId, user.id] });
    return view;
}
export async function cancel(user, orderId, emit) {
    const orderRes = await pool.query(`SELECT o.*, s.merchant_id FROM orders o JOIN stores s ON s.id = o.store_id WHERE o.id = $1`, [orderId]);
    const row = orderRes.rows[0];
    if (!row)
        throw ApiError.notFound("Order not found");
    const allowed = (user.role === "CONSUMER" && row.consumer_id === user.id) ||
        (user.role === "MERCHANT" && row.merchant_id === user.id);
    if (!allowed)
        throw ApiError.forbidden();
    if (row.status === "FULFILLED" || row.status === "CANCELLED") {
        throw ApiError.conflict("INVALID_STATUS", "Order cannot be cancelled");
    }
    await withTx(async (client) => {
        await client.query(`UPDATE orders SET status = 'CANCELLED', updated_at = NOW() WHERE id = $1`, [orderId]);
        const lines = await client.query(`SELECT * FROM order_lines WHERE order_id = $1`, [orderId]);
        for (const l of lines.rows) {
            await inventory.restore(client, row.store_id, l.product_id, l.quantity);
            const inv = await client.query(`SELECT quantity, unit_price_kobo FROM inventory_items WHERE store_id = $1 AND product_id = $2`, [row.store_id, l.product_id]);
            if (inv.rows[0]) {
                emit("stock.changed", {
                    storeId: row.store_id,
                    productId: l.product_id,
                    quantity: inv.rows[0].quantity,
                    unitPriceKobo: Number(inv.rows[0].unit_price_kobo),
                }, { broadcast: true });
            }
        }
        await payments.refund(client, orderId);
    });
    const view = await loadOrder(orderId);
    emit("order.updated", view, { userIds: [view.consumerId, row.merchant_id] });
    return view;
}
export async function verify(orderId) {
    const view = await loadOrder(orderId);
    const recomputed = ledgerHash([
        view.id,
        view.consumerId,
        view.storeId,
        String(view.totalKobo),
        ...view.items.map((l) => `${l.productId}:${l.quantity}:${l.unitPriceKobo}`),
    ]);
    const anchor = await pool.query(`SELECT b.merkle_root, b.tx_reference, b.status, i.leaf_index
     FROM anchor_items i JOIN anchor_batches b ON b.id = i.batch_id WHERE i.order_id = $1`, [orderId]);
    const a = anchor.rows[0];
    const hashMatches = recomputed === view.ledgerHash;
    return {
        orderId,
        ledgerHash: view.ledgerHash,
        hashMatches,
        valid: hashMatches,
        anchored: !!a,
        merkleRoot: a?.merkle_root ?? null,
        txReference: a?.tx_reference ?? null,
        leafIndex: a?.leaf_index ?? null,
        anchorStatus: a?.status ?? null,
        proofValid: hashMatches && !!a,
    };
}
//# sourceMappingURL=orderService.js.map