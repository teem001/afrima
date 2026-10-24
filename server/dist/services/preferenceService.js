import { v4 as uuid } from "uuid";
import { aiConfigured, config } from "../config.js";
import { pool } from "../db/pool.js";
import { ApiError } from "../errors.js";
import { formatNaira } from "../money.js";
import { complete, textContent } from "./ai/openai.js";
function parseJsonArray(raw) {
    if (!raw)
        return [];
    try {
        const v = JSON.parse(raw);
        return Array.isArray(v) ? v : [];
    }
    catch {
        return [];
    }
}
export async function trackEvent(userId, input) {
    const id = uuid();
    await pool.query(`INSERT INTO user_behavior_events
      (id, user_id, type, query, product_id, store_id, category_id, unit_price_kobo, quantity, latitude, longitude, payload, created_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,NOW())`, [
        id,
        userId,
        input.type,
        input.query?.trim().slice(0, 255) || null,
        input.productId || null,
        input.storeId || null,
        input.categoryId || null,
        input.unitPriceKobo ?? null,
        input.quantity ?? null,
        input.latitude ?? null,
        input.longitude ?? null,
        input.payload ? JSON.stringify(input.payload).slice(0, 4000) : null,
    ]);
    // Fire-and-forget recompute; failures shouldn't break the caller path.
    void recompute(userId).catch(() => undefined);
    if (input.type === "SEARCH" && input.query?.trim()) {
        void maybeAutoSubscribe(userId, input.query.trim(), input.categoryId ?? null).catch(() => undefined);
    }
    return id;
}
async function maybeAutoSubscribe(userId, query, categoryId) {
    const q = query.toLowerCase();
    const count = await pool.query(`SELECT COUNT(*)::int AS c FROM user_behavior_events
     WHERE user_id = $1 AND type = 'SEARCH' AND lower(query) = $2`, [userId, q]);
    if (Number(count.rows[0]?.c ?? 0) < 2)
        return;
    const existing = await pool.query(`SELECT id FROM notification_subscriptions
     WHERE user_id = $1 AND active = TRUE AND lower(COALESCE(query,'')) = $2
     LIMIT 1`, [userId, q]);
    if (existing.rows[0])
        return;
    const caps = await pool.query(`SELECT COUNT(*)::int AS c FROM notification_subscriptions WHERE user_id = $1 AND active = TRUE`, [userId]);
    if (Number(caps.rows[0]?.c ?? 0) >= 20)
        return;
    const prefs = await pool.query(`SELECT price_p50_kobo FROM shopper_preferences WHERE user_id = $1`, [userId]);
    const maxPrice = prefs.rows[0]?.price_p50_kobo != null ? Math.round(Number(prefs.rows[0].price_p50_kobo) * 1.25) : null;
    const now = new Date();
    await pool.query(`INSERT INTO notification_subscriptions
      (id, user_id, category_id, query, max_price_kobo, active, created_at, updated_at)
     VALUES ($1,$2,$3,$4,$5,TRUE,$6,$6)`, [uuid(), userId, categoryId, query.slice(0, 255), maxPrice, now]);
}
function percentile(sorted, p) {
    if (!sorted.length)
        return null;
    const idx = Math.min(sorted.length - 1, Math.max(0, Math.floor((sorted.length - 1) * p)));
    return sorted[idx];
}
export async function recompute(userId) {
    const events = await pool.query(`SELECT type, query, product_id, category_id, unit_price_kobo, quantity, latitude, longitude, created_at
     FROM user_behavior_events WHERE user_id = $1 ORDER BY created_at DESC LIMIT 500`, [userId]);
    const categoryCounts = new Map();
    const productCounts = new Map();
    const prices = [];
    let orderCount = 0;
    let orderSpend = 0;
    const orderTimes = [];
    let latSum = 0;
    let lngSum = 0;
    let locN = 0;
    for (const row of events.rows) {
        if (row.category_id)
            categoryCounts.set(row.category_id, (categoryCounts.get(row.category_id) ?? 0) + 1);
        if (row.product_id)
            productCounts.set(row.product_id, (productCounts.get(row.product_id) ?? 0) + 1);
        if (row.unit_price_kobo != null)
            prices.push(Number(row.unit_price_kobo));
        if (row.type === "ORDER") {
            orderCount += 1;
            orderSpend += Number(row.unit_price_kobo ?? 0) * Number(row.quantity ?? 1);
            orderTimes.push(new Date(row.created_at).getTime());
        }
        if (row.latitude != null && row.longitude != null) {
            latSum += Number(row.latitude);
            lngSum += Number(row.longitude);
            locN += 1;
        }
    }
    const topCategories = [...categoryCounts.entries()]
        .sort((a, b) => b[1] - a[1])
        .slice(0, 5)
        .map(([id, count]) => ({ categoryId: id, count }));
    const topProducts = [...productCounts.entries()]
        .sort((a, b) => b[1] - a[1])
        .slice(0, 8)
        .map(([id, count]) => ({ productId: id, count }));
    prices.sort((a, b) => a - b);
    const priceMin = prices.length ? prices[0] : null;
    const priceMax = prices.length ? prices[prices.length - 1] : null;
    const priceP50 = percentile(prices, 0.5);
    let frequencyDays = null;
    if (orderTimes.length >= 2) {
        const newest = orderTimes[0];
        const oldest = orderTimes[orderTimes.length - 1];
        const spanDays = Math.max(1, (newest - oldest) / (1000 * 60 * 60 * 24));
        frequencyDays = Number((spanDays / (orderTimes.length - 1)).toFixed(2));
    }
    // Higher when many searches vs few orders at higher prices → more deal-seeking.
    const searches = events.rows.filter((r) => r.type === "SEARCH").length;
    const discountSensitivity = Number(Math.min(1, Math.max(0, searches === 0 ? 0.5 : 0.3 + Math.min(0.5, searches / 20) - orderCount * 0.02)).toFixed(2));
    const avgOrder = orderCount ? Math.round(orderSpend / orderCount) : null;
    const now = new Date();
    await pool.query(`INSERT INTO shopper_preferences
      (user_id, top_categories, top_products, price_min_kobo, price_p50_kobo, price_max_kobo,
       preferred_radius_m, preferred_lat, preferred_lng, order_count, avg_order_kobo,
       order_frequency_days, discount_sensitivity, updated_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14)
     ON CONFLICT (user_id) DO UPDATE SET
       top_categories = EXCLUDED.top_categories,
       top_products = EXCLUDED.top_products,
       price_min_kobo = EXCLUDED.price_min_kobo,
       price_p50_kobo = EXCLUDED.price_p50_kobo,
       price_max_kobo = EXCLUDED.price_max_kobo,
       preferred_radius_m = EXCLUDED.preferred_radius_m,
       preferred_lat = EXCLUDED.preferred_lat,
       preferred_lng = EXCLUDED.preferred_lng,
       order_count = EXCLUDED.order_count,
       avg_order_kobo = EXCLUDED.avg_order_kobo,
       order_frequency_days = EXCLUDED.order_frequency_days,
       discount_sensitivity = EXCLUDED.discount_sensitivity,
       updated_at = EXCLUDED.updated_at`, [
        userId,
        JSON.stringify(topCategories),
        JSON.stringify(topProducts),
        priceMin,
        priceP50,
        priceMax,
        config.searchDefaultRadiusM,
        locN ? latSum / locN : null,
        locN ? lngSum / locN : null,
        orderCount,
        avgOrder,
        frequencyDays,
        discountSensitivity,
        now,
    ]);
}
export async function getProfile(user) {
    if (user.role !== "CONSUMER")
        throw ApiError.forbidden();
    await recompute(user.id).catch(() => undefined);
    const row = await pool.query(`SELECT * FROM shopper_preferences WHERE user_id = $1`, [user.id]);
    const prefs = row.rows[0];
    const topCategories = parseJsonArray(prefs?.top_categories);
    const topProducts = parseJsonArray(prefs?.top_products);
    const categoryIds = topCategories.map((c) => c.categoryId);
    const productIds = topProducts.map((p) => p.productId);
    const catNames = new Map();
    const prodNames = new Map();
    if (categoryIds.length) {
        const placeholders = categoryIds.map((_, i) => `$${i + 1}`).join(", ");
        const cats = await pool.query(`SELECT id, slug, name FROM product_categories WHERE id IN (${placeholders})`, categoryIds);
        for (const c of cats.rows)
            catNames.set(c.id, c.name);
    }
    if (productIds.length) {
        const placeholders = productIds.map((_, i) => `$${i + 1}`).join(", ");
        const prods = await pool.query(`SELECT id, name FROM products WHERE id IN (${placeholders})`, productIds);
        for (const p of prods.rows)
            prodNames.set(p.id, p.name);
    }
    const recentSearches = await pool.query(`SELECT query, created_at FROM user_behavior_events
     WHERE user_id = $1 AND type = 'SEARCH' AND query IS NOT NULL
     ORDER BY created_at DESC LIMIT 10`, [user.id]);
    return {
        userId: user.id,
        topCategories: topCategories.map((c) => ({
            ...c,
            name: catNames.get(c.categoryId) ?? null,
        })),
        topProducts: topProducts.map((p) => ({
            ...p,
            name: prodNames.get(p.productId) ?? null,
        })),
        priceBand: {
            minKobo: prefs?.price_min_kobo != null ? Number(prefs.price_min_kobo) : null,
            p50Kobo: prefs?.price_p50_kobo != null ? Number(prefs.price_p50_kobo) : null,
            maxKobo: prefs?.price_max_kobo != null ? Number(prefs.price_max_kobo) : null,
            min: prefs?.price_min_kobo != null ? formatNaira(Number(prefs.price_min_kobo)) : null,
            p50: prefs?.price_p50_kobo != null ? formatNaira(Number(prefs.price_p50_kobo)) : null,
            max: prefs?.price_max_kobo != null ? formatNaira(Number(prefs.price_max_kobo)) : null,
        },
        preferredRadiusM: prefs?.preferred_radius_m != null ? Number(prefs.preferred_radius_m) : null,
        preferredLocation: prefs?.preferred_lat != null && prefs?.preferred_lng != null
            ? { lat: Number(prefs.preferred_lat), lng: Number(prefs.preferred_lng) }
            : null,
        orderCount: Number(prefs?.order_count ?? 0),
        avgOrderKobo: prefs?.avg_order_kobo != null ? Number(prefs.avg_order_kobo) : null,
        avgOrder: prefs?.avg_order_kobo != null ? formatNaira(Number(prefs.avg_order_kobo)) : null,
        orderFrequencyDays: prefs?.order_frequency_days != null ? Number(prefs.order_frequency_days) : null,
        discountSensitivity: Number(prefs?.discount_sensitivity ?? 0.5),
        aiReview: prefs?.last_ai_review ?? null,
        aiReviewAt: prefs?.last_ai_review_at ?? null,
        recentSearches: recentSearches.rows.map((r) => ({
            query: r.query,
            createdAt: r.created_at,
        })),
        updatedAt: prefs?.updated_at ?? null,
    };
}
export async function generateAiReview(user) {
    if (user.role !== "CONSUMER")
        throw ApiError.forbidden();
    if (!aiConfigured()) {
        throw ApiError.badRequest("AI_UNAVAILABLE", "AI assistant is offline until OPENAI_API_KEY is set");
    }
    const profile = await getProfile(user);
    const facts = [
        `Orders placed: ${profile.orderCount}`,
        profile.avgOrder ? `Typical order size: ₦${profile.avgOrder}` : null,
        profile.orderFrequencyDays != null ? `Orders about every ${profile.orderFrequencyDays} days` : null,
        profile.priceBand.p50 ? `Median item price interest: ₦${profile.priceBand.p50}` : null,
        profile.topCategories.length
            ? `Top categories: ${profile.topCategories.map((c) => c.name ?? c.categoryId).join(", ")}`
            : null,
        profile.topProducts.length
            ? `Frequent products: ${profile.topProducts.map((p) => p.name ?? p.productId).join(", ")}`
            : null,
        profile.recentSearches.length
            ? `Recent searches: ${profile.recentSearches.map((s) => s.query).join(", ")}`
            : null,
        `Deal sensitivity (0–1): ${profile.discountSensitivity}`,
    ]
        .filter(Boolean)
        .join("\n");
    const message = await complete([
        {
            role: "system",
            content: "You write a short shopper profile for AfriSmart (Lagos marketplace). 2–4 sentences, plain language, no markdown. Cover categories, price comfort, location habits if known, and how to help them next time. If little data, say what to learn next.",
        },
        {
            role: "user",
            content: `Write an AI shopper review for ${user.email}.\n\nFacts:\n${facts || "No shopping history yet."}`,
        },
    ], []);
    const review = textContent(message.content).trim().slice(0, 2000) || "Not enough shopping history yet to write a review.";
    const now = new Date();
    await pool.query(`INSERT INTO shopper_preferences (user_id, last_ai_review, last_ai_review_at, updated_at)
     VALUES ($1,$2,$3,$3)
     ON CONFLICT (user_id) DO UPDATE SET
       last_ai_review = EXCLUDED.last_ai_review,
       last_ai_review_at = EXCLUDED.last_ai_review_at,
       updated_at = EXCLUDED.updated_at`, [user.id, review, now]);
    return { ...profile, aiReview: review, aiReviewAt: now };
}
/** Compact text for system prompt injection. */
export async function profilePromptSnippet(userId) {
    const row = await pool.query(`SELECT * FROM shopper_preferences WHERE user_id = $1`, [userId]);
    const prefs = row.rows[0];
    if (!prefs)
        return null;
    const cats = parseJsonArray(prefs.top_categories);
    let catLabel = "";
    if (cats.length) {
        const ids = cats.map((c) => c.categoryId);
        const placeholders = ids.map((_, i) => `$${i + 1}`).join(", ");
        const res = await pool.query(`SELECT id, name FROM product_categories WHERE id IN (${placeholders})`, ids);
        const names = new Map(res.rows.map((r) => [r.id, r.name]));
        catLabel = cats.map((c) => names.get(c.categoryId) ?? "category").join(", ");
    }
    const parts = [
        prefs.last_ai_review ? `AI review: ${String(prefs.last_ai_review).slice(0, 500)}` : null,
        catLabel ? `Favourite categories: ${catLabel}` : null,
        prefs.price_p50_kobo != null ? `Comfortable around ₦${formatNaira(Number(prefs.price_p50_kobo))}` : null,
        Number(prefs.order_count) > 0 ? `Past orders: ${prefs.order_count}` : null,
        `Deal sensitivity: ${prefs.discount_sensitivity}`,
    ].filter(Boolean);
    return parts.length ? parts.join("\n") : null;
}
//# sourceMappingURL=preferenceService.js.map