import { pool } from "../db/pool.js";
import { haversineMeters, isOpenNow } from "../geo.js";
import { formatNaira } from "../money.js";
import { mediaUrl } from "./mediaService.js";
const DEFAULT_PAGE_SIZE = 20;
const MAX_PAGE_SIZE = 50;
function mapOffer(row, lat, lng) {
    const distanceMeters = haversineMeters(lat, lng, Number(row.latitude), Number(row.longitude));
    const unitPriceKobo = Number(row.unit_price_kobo);
    const openAt = String(row.open_at).slice(0, 8);
    const closeAt = String(row.close_at).slice(0, 8);
    return {
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
        quantity: Number(row.quantity),
        unitPriceKobo,
        unitPrice: formatNaira(unitPriceKobo),
        currency: row.currency,
        distanceMeters: Math.round(distanceMeters),
        categoryId: row.category_id ?? null,
        categorySlug: row.category_slug ?? null,
        categoryName: row.category_name ?? null,
    };
}
export async function search(input) {
    const q = (input.q || "").trim();
    const page = Math.max(1, Math.floor(Number(input.page) || 1));
    const pageSize = Math.min(MAX_PAGE_SIZE, Math.max(1, Math.floor(Number(input.pageSize) || DEFAULT_PAGE_SIZE)));
    const radiusRaw = input.radiusMeters;
    const useRadius = radiusRaw != null && Number.isFinite(Number(radiusRaw)) && Number(radiusRaw) > 0;
    const radius = useRadius ? Math.min(50_000, Math.max(100, Number(radiusRaw))) : null;
    const params = [];
    const clauses = ["i.quantity > 0"];
    if (q) {
        params.push(`%${q.toLowerCase()}%`);
        clauses.push(`(lower(p.name) LIKE $${params.length} OR lower(p.sku) LIKE $${params.length} OR lower(COALESCE(a.alias,'')) LIKE $${params.length})`);
    }
    if (input.categoryId) {
        params.push(input.categoryId);
        clauses.push(`p.category_id = $${params.length}`);
    }
    else if (input.categorySlug) {
        params.push(input.categorySlug.toLowerCase());
        clauses.push(`c.slug = $${params.length}`);
    }
    if (radius != null) {
        const latDelta = radius / 111_320;
        const lngDelta = radius / (111_320 * Math.max(0.2, Math.cos((input.lat * Math.PI) / 180)));
        params.push(input.lat - latDelta, input.lat + latDelta, input.lng - lngDelta, input.lng + lngDelta);
        clauses.push(`s.latitude BETWEEN $${params.length - 3} AND $${params.length - 2}`, `s.longitude BETWEEN $${params.length - 1} AND $${params.length}`);
    }
    if (!q && !input.categoryId && !input.categorySlug) {
        // Require at least a query or category to avoid dumping the whole catalogue.
        return {
            query: q,
            categoryId: input.categoryId ?? null,
            categorySlug: input.categorySlug ?? null,
            latitude: input.lat,
            longitude: input.lng,
            radiusMeters: radius,
            page: 1,
            pageSize,
            totalCount: 0,
            totalPages: 1,
            hasMore: false,
            resultCount: 0,
            minPriceKobo: null,
            maxPriceKobo: null,
            minPrice: null,
            maxPrice: null,
            offers: [],
        };
    }
    const res = await pool.query(`
    SELECT s.id AS store_id, s.name AS store_name, s.address, s.latitude, s.longitude,
           s.open_at, s.close_at, s.image_media_id AS store_image_id,
           p.id AS product_id, p.sku, p.name AS product_name, p.image_media_id AS product_image_id,
           p.category_id, c.slug AS category_slug, c.name AS category_name,
           i.quantity, i.unit_price_kobo, i.currency
    FROM inventory_items i
    JOIN stores s ON s.id = i.store_id AND s.active = TRUE
    JOIN products p ON p.id = i.product_id
    LEFT JOIN product_categories c ON c.id = p.category_id
    LEFT JOIN product_aliases a ON a.product_id = p.id
    WHERE ${clauses.join(" AND ")}
    `, params);
    const seen = new Set();
    const offers = [];
    for (const row of res.rows) {
        const key = `${row.store_id}:${row.product_id}`;
        if (seen.has(key))
            continue;
        const offer = mapOffer(row, input.lat, input.lng);
        if (radius != null && offer.distanceMeters > radius)
            continue;
        seen.add(key);
        offers.push(offer);
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
        categoryId: input.categoryId ?? null,
        categorySlug: input.categorySlug ?? null,
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
//# sourceMappingURL=searchService.js.map