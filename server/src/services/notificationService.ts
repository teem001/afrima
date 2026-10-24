import { v4 as uuid } from "uuid";
import type { AuthUser } from "../auth/jwt.js";
import { pool } from "../db/pool.js";
import { ApiError } from "../errors.js";
import { formatNaira } from "../money.js";
import { emit } from "../ws/hub.js";

export type NotificationType = "PRICE_DROP" | "BACK_IN_STOCK" | "CATEGORY_MATCH" | "DISCOUNT";

function mapNotification(row: {
  id: string;
  user_id: string;
  type: string;
  title: string;
  body: string;
  href: string | null;
  payload: string | null;
  read_at: Date | null;
  created_at: Date;
}) {
  return {
    id: row.id,
    userId: row.user_id,
    type: row.type as NotificationType,
    title: row.title,
    body: row.body,
    href: row.href,
    payload: row.payload ? safeJson(row.payload) : null,
    readAt: row.read_at,
    createdAt: row.created_at,
  };
}

function safeJson(raw: string) {
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

export async function list(user: AuthUser, limit = 40) {
  const capped = Math.min(100, Math.max(1, limit));
  const res = await pool.query(
    `SELECT * FROM notifications WHERE user_id = $1 ORDER BY created_at DESC LIMIT $2`,
    [user.id, capped],
  );
  return res.rows.map(mapNotification);
}

export async function unreadCount(user: AuthUser) {
  const res = await pool.query(
    `SELECT COUNT(*)::int AS c FROM notifications WHERE user_id = $1 AND read_at IS NULL`,
    [user.id],
  );
  return { count: Number(res.rows[0]?.c ?? 0) };
}

export async function markRead(user: AuthUser, notificationId?: string | null) {
  if (notificationId) {
    await pool.query(
      `UPDATE notifications SET read_at = NOW() WHERE id = $1 AND user_id = $2 AND read_at IS NULL`,
      [notificationId, user.id],
    );
  } else {
    await pool.query(`UPDATE notifications SET read_at = NOW() WHERE user_id = $1 AND read_at IS NULL`, [
      user.id,
    ]);
  }
  return unreadCount(user);
}

export async function create(
  userId: string,
  input: {
    type: NotificationType;
    title: string;
    body: string;
    href?: string | null;
    payload?: Record<string, unknown> | null;
  },
) {
  const id = uuid();
  await pool.query(
    `INSERT INTO notifications (id, user_id, type, title, body, href, payload, created_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,NOW())`,
    [
      id,
      userId,
      input.type,
      input.title.slice(0, 255),
      input.body.slice(0, 1000),
      input.href?.slice(0, 512) ?? null,
      input.payload ? JSON.stringify(input.payload).slice(0, 4000) : null,
    ],
  );
  const view = {
    id,
    userId,
    type: input.type,
    title: input.title,
    body: input.body,
    href: input.href ?? null,
    payload: input.payload ?? null,
    readAt: null,
    createdAt: new Date(),
  };
  emit("notification.created", view, { userIds: [userId] });
  return view;
}

/** After inventory upsert/restock, notify shoppers whose watches/preferences match. */
export async function matchInventoryUpdate(input: {
  storeId: string;
  productId: string;
  quantity: number;
  unitPriceKobo: number;
  previousQuantity?: number | null;
}) {
  if (input.quantity < 1) return { notified: 0 };

  const product = await pool.query(
    `SELECT p.id, p.name, p.category_id, c.slug AS category_slug, c.name AS category_name, s.name AS store_name
     FROM products p
     LEFT JOIN product_categories c ON c.id = p.category_id
     JOIN stores s ON s.id = $2
     WHERE p.id = $1`,
    [input.productId, input.storeId],
  );
  if (!product.rows[0]) return { notified: 0 };
  const p = product.rows[0];
  const name = String(p.name);
  const categoryId = p.category_id as string | null;
  const storeName = String(p.store_name);
  const backInStock = (input.previousQuantity ?? 0) <= 0 && input.quantity > 0;

  const subs = await pool.query(
    `SELECT * FROM notification_subscriptions WHERE active = TRUE LIMIT 500`,
  );

  const notifiedUsers = new Set<string>();
  for (const sub of subs.rows) {
    const userId = sub.user_id as string;
    if (notifiedUsers.has(userId)) continue;
    const query = (sub.query as string | null)?.toLowerCase().trim() || "";
    const subCat = sub.category_id as string | null;
    const maxPrice = sub.max_price_kobo != null ? Number(sub.max_price_kobo) : null;

    const queryHit = query ? name.toLowerCase().includes(query) : false;
    const catHit = subCat && categoryId ? subCat === categoryId : false;
    if (!queryHit && !catHit) continue;
    if (maxPrice != null && input.unitPriceKobo > maxPrice) continue;

    const type: NotificationType = backInStock
      ? "BACK_IN_STOCK"
      : maxPrice != null && input.unitPriceKobo <= maxPrice
        ? "PRICE_DROP"
        : "CATEGORY_MATCH";
    const title =
      type === "BACK_IN_STOCK"
        ? `${name} is back in stock`
        : type === "PRICE_DROP"
          ? `${name} matches your price watch`
          : `New match: ${name}`;
    await create(userId, {
      type,
      title,
      body: `${storeName} has ${name} at ₦${formatNaira(input.unitPriceKobo)} (${input.quantity} in stock).`,
      href: `/?q=${encodeURIComponent(name)}`,
      payload: {
        storeId: input.storeId,
        productId: input.productId,
        unitPriceKobo: input.unitPriceKobo,
        categoryId,
      },
    });
    notifiedUsers.add(userId);
  }

  // Also nudge users whose top category preference matches and price is within band.
  if (categoryId) {
    const prefs = await pool.query(
      `SELECT user_id, top_categories, price_p50_kobo, price_max_kobo FROM shopper_preferences LIMIT 300`,
    );
    for (const pref of prefs.rows) {
      const userId = pref.user_id as string;
      if (notifiedUsers.has(userId)) continue;
      let tops: { categoryId: string; count: number }[] = [];
      try {
        tops = JSON.parse(pref.top_categories || "[]");
      } catch {
        tops = [];
      }
      if (!tops.some((t) => t.categoryId === categoryId && t.count >= 2)) continue;
      const band = pref.price_max_kobo != null ? Number(pref.price_max_kobo) : pref.price_p50_kobo != null ? Number(pref.price_p50_kobo) * 1.4 : null;
      if (band != null && input.unitPriceKobo > band) continue;
      await create(userId, {
        type: "CATEGORY_MATCH",
        title: `${name} matches your usual shopping`,
        body: `${storeName} stocked ${name} at ₦${formatNaira(input.unitPriceKobo)} — in a category you buy often.`,
        href: `/?q=${encodeURIComponent(name)}`,
        payload: { storeId: input.storeId, productId: input.productId, categoryId },
      });
      notifiedUsers.add(userId);
    }
  }

  return { notified: notifiedUsers.size };
}

export async function listSubscriptions(user: AuthUser) {
  const res = await pool.query(
    `SELECT s.*, c.name AS category_name, c.slug AS category_slug
     FROM notification_subscriptions s
     LEFT JOIN product_categories c ON c.id = s.category_id
     WHERE s.user_id = $1
     ORDER BY s.updated_at DESC`,
    [user.id],
  );
  return res.rows.map((r) => ({
    id: r.id,
    categoryId: r.category_id,
    categoryName: r.category_name,
    categorySlug: r.category_slug,
    query: r.query,
    maxPriceKobo: r.max_price_kobo != null ? Number(r.max_price_kobo) : null,
    active: r.active,
    createdAt: r.created_at,
  }));
}

export async function upsertSubscription(
  user: AuthUser,
  input: { categoryId?: string | null; query?: string | null; maxPriceKobo?: number | null; active?: boolean },
) {
  if (user.role !== "CONSUMER") throw ApiError.forbidden();
  const query = input.query?.trim().slice(0, 255) || null;
  const categoryId = input.categoryId || null;
  if (!query && !categoryId) throw ApiError.badRequest("VALIDATION_FAILED", "query or categoryId required");
  const now = new Date();
  const existing = await pool.query(
    `SELECT id FROM notification_subscriptions
     WHERE user_id = $1 AND COALESCE(category_id::text,'') = COALESCE($2::text,'')
       AND lower(COALESCE(query,'')) = lower(COALESCE($3,''))
     LIMIT 1`,
    [user.id, categoryId, query],
  );
  if (existing.rows[0]) {
    await pool.query(
      `UPDATE notification_subscriptions
       SET max_price_kobo = $2, active = $3, updated_at = $4 WHERE id = $1`,
      [existing.rows[0].id, input.maxPriceKobo ?? null, input.active !== false, now],
    );
    return listSubscriptions(user).then((list) => list.find((s) => s.id === existing.rows[0].id));
  }
  const id = uuid();
  await pool.query(
    `INSERT INTO notification_subscriptions
      (id, user_id, category_id, query, max_price_kobo, active, created_at, updated_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$7)`,
    [id, user.id, categoryId, query, input.maxPriceKobo ?? null, input.active !== false, now],
  );
  return listSubscriptions(user).then((list) => list.find((s) => s.id === id));
}
