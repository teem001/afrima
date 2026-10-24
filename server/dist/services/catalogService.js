import { v4 as uuid } from "uuid";
import { pool } from "../db/pool.js";
import { ApiError } from "../errors.js";
import { mediaUrl } from "./mediaService.js";
import { formatNaira } from "../money.js";
const OTHER_CATEGORY_ID = "a1000000-0000-4000-8000-000000000008";
export async function listCategories() {
    const res = await pool.query(`SELECT id, slug, name, parent_id FROM product_categories ORDER BY name`);
    return res.rows.map((c) => ({
        id: c.id,
        slug: c.slug,
        name: c.name,
        parentId: c.parent_id ?? null,
    }));
}
async function resolveCategoryId(input) {
    if (!input?.trim())
        return OTHER_CATEGORY_ID;
    const value = input.trim();
    const looksLikeUuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
    if (looksLikeUuid) {
        const byId = await pool.query(`SELECT id FROM product_categories WHERE id = $1`, [value]);
        if (byId.rows[0])
            return byId.rows[0].id;
    }
    const bySlug = await pool.query(`SELECT id FROM product_categories WHERE slug = $1`, [value.toLowerCase()]);
    if (bySlug.rows[0])
        return bySlug.rows[0].id;
    throw ApiError.badRequest("VALIDATION_FAILED", "Unknown category");
}
export async function listProducts() {
    const products = await pool.query(`SELECT p.id, p.sku, p.name, p.description, p.image_media_id, p.category_id,
            c.slug AS category_slug, c.name AS category_name
     FROM products p
     LEFT JOIN product_categories c ON c.id = p.category_id
     ORDER BY p.name`);
    const aliases = await pool.query(`SELECT product_id, alias FROM product_aliases`);
    const byProduct = new Map();
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
        categoryId: p.category_id ?? OTHER_CATEGORY_ID,
        categorySlug: p.category_slug ?? "other",
        categoryName: p.category_name ?? "Other",
    }));
}
export async function createProduct(user, input) {
    if (user.role !== "MERCHANT")
        throw ApiError.forbidden("Merchants only");
    const name = input.name?.trim();
    if (!name)
        throw ApiError.badRequest("VALIDATION_FAILED", "Name required");
    let sku = input.sku?.trim() || "";
    if (!sku) {
        sku = `SKU-${Date.now().toString(36).toUpperCase()}-${Math.floor(Math.random() * 900 + 100)}`;
    }
    const categoryId = await resolveCategoryId(input.categoryId || input.categorySlug || null);
    const id = uuid();
    const now = new Date();
    try {
        await pool.query(`INSERT INTO products (id, sku, name, description, image_media_id, category_id, created_at, updated_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$7)`, [id, sku, name, input.description?.trim() || null, input.imageMediaId || null, categoryId, now]);
    }
    catch {
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
    const cats = await pool.query(`SELECT slug, name FROM product_categories WHERE id = $1`, [categoryId]);
    return {
        id,
        sku,
        name,
        description: input.description?.trim() || null,
        aliases,
        imageMediaId: input.imageMediaId || null,
        imageUrl: mediaUrl(input.imageMediaId),
        categoryId,
        categorySlug: cats.rows[0]?.slug ?? "other",
        categoryName: cats.rows[0]?.name ?? "Other",
    };
}
export { formatNaira, OTHER_CATEGORY_ID };
//# sourceMappingURL=catalogService.js.map