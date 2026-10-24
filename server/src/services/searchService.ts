import { pool } from "../db/pool.js";
import { ApiError } from "../errors.js";
import { aiConfigured } from "../config.js";
import { haversineMeters, isOpenNow } from "../geo.js";
import { formatNaira } from "../money.js";
import { getBytes, mediaUrl } from "./mediaService.js";
import { complete, textContent } from "./ai/openai.js";

const DEFAULT_PAGE_SIZE = 20;
const MAX_PAGE_SIZE = 50;

export type SearchInput = {
  q?: string;
  lat: number;
  lng: number;
  /** When set, only offers within this radius. When omitted, any in-stock match from any store. */
  radiusMeters?: number | null;
  page?: number;
  pageSize?: number;
  categoryId?: string | null;
  categorySlug?: string | null;
};

function mapOffer(row: Record<string, unknown>, lat: number, lng: number) {
  const distanceMeters = haversineMeters(lat, lng, Number(row.latitude), Number(row.longitude));
  const unitPriceKobo = Number(row.unit_price_kobo);
  const openAt = String(row.open_at).slice(0, 8);
  const closeAt = String(row.close_at).slice(0, 8);
  return {
    storeId: row.store_id as string,
    storeName: row.store_name as string,
    address: row.address as string,
    latitude: Number(row.latitude),
    longitude: Number(row.longitude),
    openNow: isOpenNow(openAt, closeAt),
    storeImageUrl: mediaUrl(row.store_image_id as string | null),
    productId: row.product_id as string,
    sku: row.sku as string,
    productName: row.product_name as string,
    productImageUrl: mediaUrl(row.product_image_id as string | null),
    quantity: Number(row.quantity),
    unitPriceKobo,
    unitPrice: formatNaira(unitPriceKobo),
    currency: row.currency as string,
    distanceMeters: Math.round(distanceMeters),
    categoryId: (row.category_id as string | null) ?? null,
    categorySlug: (row.category_slug as string | null) ?? null,
    categoryName: (row.category_name as string | null) ?? null,
  };
}

export async function search(input: SearchInput) {
  const q = (input.q || "").trim();
  const page = Math.max(1, Math.floor(Number(input.page) || 1));
  const pageSize = Math.min(
    MAX_PAGE_SIZE,
    Math.max(1, Math.floor(Number(input.pageSize) || DEFAULT_PAGE_SIZE)),
  );
  const radiusRaw = input.radiusMeters;
  const useRadius = radiusRaw != null && Number.isFinite(Number(radiusRaw)) && Number(radiusRaw) > 0;
  const radius = useRadius ? Math.min(50_000, Math.max(100, Number(radiusRaw))) : null;

  const params: unknown[] = [];
  const clauses: string[] = ["i.quantity > 0"];

  if (q) {
    params.push(`%${q.toLowerCase()}%`);
    clauses.push(
      `(lower(p.name) LIKE $${params.length} OR lower(p.sku) LIKE $${params.length} OR lower(COALESCE(a.alias,'')) LIKE $${params.length})`,
    );
  }

  if (input.categoryId) {
    params.push(input.categoryId);
    clauses.push(`p.category_id = $${params.length}`);
  } else if (input.categorySlug) {
    params.push(input.categorySlug.toLowerCase());
    clauses.push(`c.slug = $${params.length}`);
  }

  if (radius != null) {
    const latDelta = radius / 111_320;
    const lngDelta = radius / (111_320 * Math.max(0.2, Math.cos((input.lat * Math.PI) / 180)));
    params.push(input.lat - latDelta, input.lat + latDelta, input.lng - lngDelta, input.lng + lngDelta);
    clauses.push(
      `s.latitude BETWEEN $${params.length - 3} AND $${params.length - 2}`,
      `s.longitude BETWEEN $${params.length - 1} AND $${params.length}`,
    );
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
      offers: [] as ReturnType<typeof mapOffer>[],
    };
  }

  const res = await pool.query(
    `
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
    `,
    params,
  );

  const seen = new Set<string>();
  const offers = [];
  for (const row of res.rows) {
    const key = `${row.store_id}:${row.product_id}`;
    if (seen.has(key)) continue;
    const offer = mapOffer(row, input.lat, input.lng);
    if (radius != null && offer.distanceMeters > radius) continue;
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

type VisionGuess = {
  description: string;
  queries: string[];
  categorySlug: string | null;
};

async function identifyProductFromImage(dataUrl: string): Promise<VisionGuess> {
  if (!aiConfigured()) {
    throw ApiError.badRequest("AI_UNAVAILABLE", "Image search needs OPENAI_API_KEY on the server");
  }
  const message = await complete(
    [
      {
        role: "system",
        content:
          'You identify grocery/household products in photos for a Lagos marketplace search. Reply with ONLY JSON: {"description":"short what you see","queries":["search phrase 1","search phrase 2"],"categorySlug":"staples|beverages|household|personal_care|snacks|fresh|electronics|other|null"}. queries should be 1–3 short shopper search terms (brand + product if clear).',
      },
      {
        role: "user",
        content: [
          { type: "text", text: "What product is this? Give marketplace search queries." },
          { type: "image_url", image_url: { url: dataUrl, detail: "low" } },
        ],
      },
    ],
    [],
  );
  const raw = textContent(message.content);
  const jsonMatch = raw.match(/\{[\s\S]*\}/);
  if (!jsonMatch) {
    return { description: raw.slice(0, 200) || "Product in photo", queries: ["product"], categorySlug: null };
  }
  try {
    const parsed = JSON.parse(jsonMatch[0]) as {
      description?: string;
      queries?: unknown;
      categorySlug?: string | null;
    };
    const queries = Array.isArray(parsed.queries)
      ? parsed.queries.map((q) => String(q).trim()).filter(Boolean).slice(0, 3)
      : [];
    const slug = parsed.categorySlug && parsed.categorySlug !== "null" ? String(parsed.categorySlug) : null;
    return {
      description: String(parsed.description || "Product in photo").slice(0, 240),
      queries: queries.length ? queries : ["product"],
      categorySlug: slug,
    };
  } catch {
    return { description: "Product in photo", queries: ["product"], categorySlug: null };
  }
}

/** Vision → text queries → in-stock offers (same ranking as keyword search). */
export async function searchByImage(input: {
  mediaId: string;
  lat: number;
  lng: number;
  radiusMeters?: number | null;
  page?: number;
  pageSize?: number;
}) {
  const asset = await getBytes(input.mediaId);
  if (!asset) throw ApiError.notFound("Image not found");
  if (!asset.contentType.startsWith("image/")) {
    throw ApiError.badRequest("VALIDATION_FAILED", "Image search requires a photo, not a video");
  }
  const dataUrl = `data:${asset.contentType};base64,${asset.data.toString("base64")}`;
  const guess = await identifyProductFromImage(dataUrl);

  const seen = new Map<string, ReturnType<typeof mapOffer>>();
  for (const q of guess.queries) {
    const page = await search({
      q,
      lat: input.lat,
      lng: input.lng,
      radiusMeters: input.radiusMeters ?? null,
      page: 1,
      pageSize: 30,
      categorySlug: null,
    });
    for (const offer of page.offers) {
      const key = `${offer.storeId}:${offer.productId}`;
      if (!seen.has(key)) seen.set(key, offer);
    }
  }

  // If text queries miss, try category browse as a soft fallback.
  if (!seen.size && guess.categorySlug) {
    const page = await search({
      lat: input.lat,
      lng: input.lng,
      radiusMeters: input.radiusMeters ?? null,
      page: 1,
      pageSize: 30,
      categorySlug: guess.categorySlug,
    });
    for (const offer of page.offers) {
      const key = `${offer.storeId}:${offer.productId}`;
      if (!seen.has(key)) seen.set(key, offer);
    }
  }

  const offers = [...seen.values()].sort(
    (a, b) => a.distanceMeters - b.distanceMeters || a.unitPriceKobo - b.unitPriceKobo,
  );
  const pageSize = Math.min(50, Math.max(1, Math.floor(Number(input.pageSize) || 20)));
  const page = Math.max(1, Math.floor(Number(input.page) || 1));
  const totalCount = offers.length;
  const totalPages = Math.max(1, Math.ceil(totalCount / pageSize));
  const safePage = Math.min(page, totalPages);
  const start = (safePage - 1) * pageSize;
  const pageOffers = offers.slice(start, start + pageSize);
  const prices = offers.map((o) => o.unitPriceKobo);
  const minPriceKobo = prices.length ? Math.min(...prices) : null;
  const maxPriceKobo = prices.length ? Math.max(...prices) : null;
  const primaryQuery = guess.queries[0] || guess.description;

  return {
    query: primaryQuery,
    queries: guess.queries,
    visionDescription: guess.description,
    categorySlug: guess.categorySlug,
    mediaId: input.mediaId,
    latitude: input.lat,
    longitude: input.lng,
    radiusMeters: input.radiusMeters ?? null,
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
