import { pool } from "../db/pool.js";
import { config } from "../config.js";
import { haversineMeters, isOpenNow } from "../geo.js";
import { formatNaira } from "../money.js";
import { mediaUrl } from "./mediaService.js";

export async function search(input: { q: string; lat: number; lng: number; radiusMeters?: number }) {
  const q = input.q.trim();
  const radius = Math.min(50_000, Math.max(100, input.radiusMeters ?? config.searchDefaultRadiusM));
  const latDelta = radius / 111_320;
  const lngDelta = radius / (111_320 * Math.max(0.2, Math.cos((input.lat * Math.PI) / 180)));

  const res = await pool.query(
    `
    SELECT s.id AS store_id, s.name AS store_name, s.address, s.latitude, s.longitude,
           s.open_at, s.close_at, s.image_media_id AS store_image_id,
           p.id AS product_id, p.sku, p.name AS product_name, p.image_media_id AS product_image_id,
           i.quantity, i.unit_price_kobo, i.currency
    FROM inventory_items i
    JOIN stores s ON s.id = i.store_id AND s.active = TRUE
    JOIN products p ON p.id = i.product_id
    LEFT JOIN product_aliases a ON a.product_id = p.id
    WHERE i.quantity > 0
      AND s.latitude BETWEEN $1 AND $2
      AND s.longitude BETWEEN $3 AND $4
      AND (
        lower(p.name) LIKE $5 OR lower(p.sku) LIKE $5 OR lower(COALESCE(a.alias,'')) LIKE $5
      )
    `,
    [
      input.lat - latDelta,
      input.lat + latDelta,
      input.lng - lngDelta,
      input.lng + lngDelta,
      `%${q.toLowerCase()}%`,
    ],
  );

  const seen = new Set<string>();
  const offers = [];
  for (const row of res.rows) {
    const key = `${row.store_id}:${row.product_id}`;
    if (seen.has(key)) continue;
    const distanceMeters = haversineMeters(input.lat, input.lng, Number(row.latitude), Number(row.longitude));
    if (distanceMeters > radius) continue;
    seen.add(key);
    const unitPriceKobo = Number(row.unit_price_kobo);
    const openAt = String(row.open_at).slice(0, 8);
    const closeAt = String(row.close_at).slice(0, 8);
    offers.push({
      storeId: row.store_id,
      storeName: row.store_name,
      address: row.address,
      latitude: Number(row.latitude),
      longitude: Number(row.longitude),
      openNow: isOpenNow(openAt, closeAt),
      storeImageUrl: mediaUrl(row.store_image_id),
      productId: row.product_id,
      sku: row.sku,
      productName: row.product_name,
      productImageUrl: mediaUrl(row.product_image_id),
      quantity: row.quantity,
      unitPriceKobo,
      unitPrice: formatNaira(unitPriceKobo),
      currency: row.currency,
      distanceMeters: Math.round(distanceMeters),
    });
  }

  offers.sort((a, b) => a.distanceMeters - b.distanceMeters || a.unitPriceKobo - b.unitPriceKobo);
  const limited = offers.slice(0, config.searchMaxResults);
  const prices = limited.map((o) => o.unitPriceKobo);
  const minPriceKobo = prices.length ? Math.min(...prices) : null;
  const maxPriceKobo = prices.length ? Math.max(...prices) : null;

  return {
    query: q,
    latitude: input.lat,
    longitude: input.lng,
    radiusMeters: radius,
    resultCount: limited.length,
    minPriceKobo,
    maxPriceKobo,
    minPrice: minPriceKobo == null ? null : formatNaira(minPriceKobo),
    maxPrice: maxPriceKobo == null ? null : formatNaira(maxPriceKobo),
    offers: limited,
  };
}
