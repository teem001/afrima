import { pool } from "../db/pool.js";
import { config } from "../config.js";
import { haversineMeters, isOpenNow } from "../geo.js";
import { formatNaira } from "../money.js";
import { mediaUrl } from "./mediaService.js";

const DEFAULT_PAGE_SIZE = 20;
const MAX_PAGE_SIZE = 50;

export async function search(input: {
  q: string;
  lat: number;
  lng: number;
  /** When set, only offers within this radius. When omitted, any in-stock match from any store. */
  radiusMeters?: number | null;
  page?: number;
  pageSize?: number;
}) {
  const q = input.q.trim();
  const page = Math.max(1, Math.floor(Number(input.page) || 1));
  const pageSize = Math.min(
    MAX_PAGE_SIZE,
    Math.max(1, Math.floor(Number(input.pageSize) || DEFAULT_PAGE_SIZE)),
  );
  const radiusRaw = input.radiusMeters;
  const useRadius = radiusRaw != null && Number.isFinite(Number(radiusRaw)) && Number(radiusRaw) > 0;
  const radius = useRadius
    ? Math.min(50_000, Math.max(100, Number(radiusRaw)))
    : null;

  const params: unknown[] = [`%${q.toLowerCase()}%`];
  let geoClause = "";
  if (radius != null) {
    const latDelta = radius / 111_320;
    const lngDelta = radius / (111_320 * Math.max(0.2, Math.cos((input.lat * Math.PI) / 180)));
    params.push(input.lat - latDelta, input.lat + latDelta, input.lng - lngDelta, input.lng + lngDelta);
    geoClause = `AND s.latitude BETWEEN $2 AND $3 AND s.longitude BETWEEN $4 AND $5`;
  }

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
      ${geoClause}
      AND (
        lower(p.name) LIKE $1 OR lower(p.sku) LIKE $1 OR lower(COALESCE(a.alias,'')) LIKE $1
      )
    `,
    params,
  );

  const seen = new Set<string>();
  const offers = [];
  for (const row of res.rows) {
    const key = `${row.store_id}:${row.product_id}`;
    if (seen.has(key)) continue;
    const distanceMeters = haversineMeters(input.lat, input.lng, Number(row.latitude), Number(row.longitude));
    if (radius != null && distanceMeters > radius) continue;
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

  const totalCount = offers.length;
  const totalPages = Math.max(1, Math.ceil(totalCount / pageSize));
  const safePage = Math.min(page, totalPages);
  const start = (safePage - 1) * pageSize;
  const pageOffers = offers.slice(start, start + pageSize);

  const prices = offers.map((o) => o.unitPriceKobo);
  const minPriceKobo = prices.length ? Math.min(...prices) : null;
  const maxPriceKobo = prices.length ? Math.max(...prices) : null;

  return {
    query: q,
    latitude: input.lat,
    longitude: input.lng,
    radiusMeters: radius,
    page: safePage,
    pageSize,
    totalCount,
    totalPages,
    hasMore: safePage < totalPages,
    resultCount: pageOffers.length,
    minPriceKobo,
    maxPriceKobo,
    minPrice: minPriceKobo == null ? null : formatNaira(minPriceKobo),
    maxPrice: maxPriceKobo == null ? null : formatNaira(maxPriceKobo),
    offers: pageOffers,
  };
}
