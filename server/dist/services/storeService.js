import { v4 as uuid } from "uuid";
import { pool } from "../db/pool.js";
import { ApiError } from "../errors.js";
import { isOpenNow, normalizeTime } from "../geo.js";
import { mediaUrl } from "./mediaService.js";
function mapStore(row) {
    const openAt = String(row.open_at).slice(0, 8);
    const closeAt = String(row.close_at).slice(0, 8);
    return {
        id: row.id,
        merchantId: row.merchant_id,
        name: row.name,
        description: row.description,
        address: row.address,
        latitude: Number(row.latitude),
        longitude: Number(row.longitude),
        openAt,
        closeAt,
        deliveryRadiusM: row.delivery_radius_m,
        active: row.active,
        openNow: isOpenNow(openAt, closeAt),
        imageMediaId: row.image_media_id,
        imageUrl: mediaUrl(row.image_media_id),
        phone: row.phone,
        category: row.category,
    };
}
export async function listActive() {
    const res = await pool.query(`SELECT * FROM stores WHERE active = TRUE ORDER BY name`);
    return res.rows.map(mapStore);
}
export async function mine(user) {
    if (user.role !== "MERCHANT")
        throw ApiError.forbidden();
    const res = await pool.query(`SELECT * FROM stores WHERE merchant_id = $1 ORDER BY name`, [user.id]);
    return res.rows.map(mapStore);
}
export async function get(storeId) {
    const res = await pool.query(`SELECT * FROM stores WHERE id = $1`, [storeId]);
    if (!res.rows[0])
        throw ApiError.notFound("Store not found");
    return mapStore(res.rows[0]);
}
export async function create(user, input) {
    if (user.role !== "MERCHANT")
        throw ApiError.forbidden();
    const id = uuid();
    const now = new Date();
    await pool.query(`INSERT INTO stores (
      id, merchant_id, name, description, address, latitude, longitude,
      open_at, close_at, delivery_radius_m, active, image_media_id, phone, category, created_at, updated_at
    ) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,TRUE,$11,$12,$13,$14,$14)`, [
        id,
        user.id,
        input.name.trim(),
        input.description?.trim() || null,
        input.address.trim(),
        input.latitude,
        input.longitude,
        normalizeTime(input.openAt),
        normalizeTime(input.closeAt),
        input.deliveryRadiusM ?? 5000,
        input.imageMediaId || null,
        input.phone?.trim() || null,
        input.category?.trim() || null,
        now,
    ]);
    return get(id);
}
export async function update(user, storeId, input) {
    if (user.role !== "MERCHANT")
        throw ApiError.forbidden();
    const existing = await pool.query(`SELECT merchant_id FROM stores WHERE id = $1`, [storeId]);
    if (!existing.rows[0])
        throw ApiError.notFound("Store not found");
    if (existing.rows[0].merchant_id !== user.id)
        throw ApiError.forbidden();
    await pool.query(`UPDATE stores SET
      name=$2, description=$3, address=$4, latitude=$5, longitude=$6,
      open_at=$7, close_at=$8, delivery_radius_m=$9, image_media_id=$10, phone=$11, category=$12, updated_at=$13
     WHERE id=$1`, [
        storeId,
        input.name.trim(),
        input.description?.trim() || null,
        input.address.trim(),
        input.latitude,
        input.longitude,
        normalizeTime(input.openAt),
        normalizeTime(input.closeAt),
        input.deliveryRadiusM ?? 5000,
        input.imageMediaId || null,
        input.phone?.trim() || null,
        input.category?.trim() || null,
        new Date(),
    ]);
    return get(storeId);
}
//# sourceMappingURL=storeService.js.map