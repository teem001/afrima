import { v4 as uuid } from "uuid";
import { pool } from "../db/pool.js";
import { ApiError } from "../errors.js";
import * as preferences from "./preferenceService.js";
function mapReview(row) {
    return {
        id: row.id,
        productId: row.product_id,
        userId: row.user_id,
        orderId: row.order_id,
        rating: Number(row.rating),
        comment: row.comment,
        createdAt: row.created_at,
        updatedAt: row.updated_at,
        authorName: row.full_name ?? null,
        productName: row.product_name ?? null,
    };
}
async function assertPurchased(userId, productId, orderId) {
    const params = [userId, productId];
    let orderFilter = "";
    if (orderId) {
        params.push(orderId);
        orderFilter = `AND o.id = $3`;
    }
    const res = await pool.query(`SELECT o.id
     FROM orders o
     JOIN order_lines l ON l.order_id = o.id
     WHERE o.consumer_id = $1
       AND l.product_id = $2
       AND o.status <> 'CANCELLED'
       ${orderFilter}
     LIMIT 1`, params);
    if (!res.rows[0]) {
        throw ApiError.badRequest("REVIEW_NOT_ALLOWED", "You can only review products you have ordered");
    }
    return String(res.rows[0].id);
}
export async function create(user, input) {
    if (user.role !== "CONSUMER")
        throw ApiError.forbidden();
    const productId = String(input.productId || "").trim();
    if (!productId)
        throw ApiError.badRequest("VALIDATION_FAILED", "productId is required");
    const rating = Math.round(Number(input.rating));
    if (!Number.isFinite(rating) || rating < 1 || rating > 5) {
        throw ApiError.badRequest("VALIDATION_FAILED", "rating must be between 1 and 5");
    }
    const comment = input.comment?.trim() ? input.comment.trim().slice(0, 2000) : null;
    const orderId = await assertPurchased(user.id, productId, input.orderId ?? null);
    const product = await pool.query(`SELECT id FROM products WHERE id = $1`, [productId]);
    if (!product.rows[0])
        throw ApiError.notFound("Product not found");
    const existing = await pool.query(`SELECT id FROM product_reviews WHERE product_id = $1 AND user_id = $2`, [productId, user.id]);
    const now = new Date();
    if (existing.rows[0]) {
        await pool.query(`UPDATE product_reviews
       SET rating = $1, comment = $2, order_id = $3, updated_at = $4
       WHERE id = $5`, [rating, comment, orderId, now, existing.rows[0].id]);
        return get(String(existing.rows[0].id));
    }
    const id = uuid();
    await pool.query(`INSERT INTO product_reviews (id, product_id, user_id, order_id, rating, comment, created_at, updated_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$7)`, [id, productId, user.id, orderId, rating, comment, now]);
    const created = await get(id);
    const cat = await pool.query(`SELECT category_id FROM products WHERE id = $1`, [productId]);
    void preferences
        .trackEvent(user.id, {
        type: "REVIEW",
        productId,
        categoryId: cat.rows[0]?.category_id ?? null,
        payload: { rating, orderId },
    })
        .catch(() => undefined);
    return created;
}
export async function get(reviewId) {
    const res = await pool.query(`SELECT r.*, u.full_name, p.name AS product_name
     FROM product_reviews r
     JOIN users u ON u.id = r.user_id
     JOIN products p ON p.id = r.product_id
     WHERE r.id = $1`, [reviewId]);
    if (!res.rows[0])
        throw ApiError.notFound("Review not found");
    return mapReview(res.rows[0]);
}
export async function forProduct(productId, limit = 20) {
    const capped = Math.min(50, Math.max(1, limit));
    const list = await pool.query(`SELECT r.*, u.full_name, p.name AS product_name
     FROM product_reviews r
     JOIN users u ON u.id = r.user_id
     JOIN products p ON p.id = r.product_id
     WHERE r.product_id = $1
     ORDER BY r.created_at DESC
     LIMIT $2`, [productId, capped]);
    const stats = await pool.query(`SELECT COUNT(*)::int AS count, COALESCE(AVG(rating), 0)::float AS average
     FROM product_reviews WHERE product_id = $1`, [productId]);
    return {
        productId,
        count: Number(stats.rows[0]?.count ?? 0),
        averageRating: Number(Number(stats.rows[0]?.average ?? 0).toFixed(2)),
        reviews: list.rows.map(mapReview),
    };
}
export async function forOrder(user, orderId) {
    const order = await pool.query(`SELECT * FROM orders WHERE id = $1`, [orderId]);
    if (!order.rows[0])
        throw ApiError.notFound("Order not found");
    if (user.role === "CONSUMER" && order.rows[0].consumer_id !== user.id)
        throw ApiError.forbidden();
    const lines = await pool.query(`SELECT product_id FROM order_lines WHERE order_id = $1`, [orderId]);
    const productIds = lines.rows.map((r) => r.product_id);
    if (!productIds.length)
        return { orderId, items: [] };
    const placeholders = productIds.map((_, i) => `$${i + 2}`).join(", ");
    const reviews = await pool.query(`SELECT r.*, u.full_name, p.name AS product_name
     FROM product_reviews r
     JOIN users u ON u.id = r.user_id
     JOIN products p ON p.id = r.product_id
     WHERE r.user_id = $1 AND r.product_id IN (${placeholders})`, [order.rows[0].consumer_id, ...productIds]);
    const byProduct = new Map(reviews.rows.map((r) => [r.product_id, mapReview(r)]));
    return {
        orderId,
        items: productIds.map((productId) => ({
            productId,
            review: byProduct.get(productId) ?? null,
        })),
    };
}
//# sourceMappingURL=reviewService.js.map