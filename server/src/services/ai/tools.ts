import type { AuthUser } from "../../auth/jwt.js";
import { ApiError } from "../../errors.js";
import { emit } from "../../ws/hub.js";
import * as search from "../searchService.js";
import * as stores from "../storeService.js";
import * as inventory from "../inventoryService.js";
import * as orders from "../orderService.js";
import * as payments from "../paymentService.js";
import * as reviews from "../reviewService.js";
import * as catalog from "../catalogService.js";
import * as preferences from "../preferenceService.js";
import type { ToolSpec } from "./openai.js";
import type { AiClientContext } from "./prompts.js";

export type ToolContext = {
  user: AuthUser;
  latitude?: number | null;
  longitude?: number | null;
  cart?: AiClientContext["cart"] | null;
};

export type ToolResult = {
  success: boolean;
  message?: string;
  errorCode?: string;
  data?: unknown;
};

type ToolDef = {
  name: string;
  description: string;
  roles: Array<"CONSUMER" | "MERCHANT">;
  parameters: Record<string, unknown>;
  execute: (args: Record<string, unknown>, ctx: ToolContext) => Promise<ToolResult>;
};

function num(args: Record<string, unknown>, key: string, fallback?: number): number | undefined {
  const v = args[key];
  if (v === undefined || v === null || v === "") return fallback;
  const n = Number(v);
  return Number.isFinite(n) ? n : fallback;
}

function str(args: Record<string, unknown>, key: string): string {
  const v = args[key];
  if (typeof v !== "string" || !v.trim()) throw Object.assign(new Error(`${key} is required`), { code: "INVALID_ARGUMENT" });
  return v.trim();
}

function requireLocation(ctx: ToolContext, args: Record<string, unknown>) {
  const lat = num(args, "latitude", ctx.latitude ?? undefined);
  const lng = num(args, "longitude", ctx.longitude ?? undefined);
  if (lat == null || lng == null) {
    throw Object.assign(new Error("Location unknown — ask the user to share their location"), {
      code: "LOCATION_REQUIRED",
    });
  }
  return { lat, lng };
}

const TOOLS: ToolDef[] = [
  {
    name: "SEARCH_PRODUCTS_NEARBY",
    description:
      "Find nearby stores that have a product in stock, nearest first. Use the shopper's words as the query.",
    roles: ["CONSUMER"],
    parameters: {
      type: "object",
      properties: {
        query: { type: "string", description: "What the user wants to buy" },
        minQuantity: { type: "integer", description: "Minimum stock. Default 1." },
        radiusMeters: { type: "integer", description: "Search radius in meters" },
        latitude: { type: "number" },
        longitude: { type: "number" },
      },
      required: ["query"],
    },
    async execute(args, ctx) {
      const query = str(args, "query");
      const { lat, lng } = requireLocation(ctx, args);
      const radiusMeters = num(args, "radiusMeters");
      const result = await search.search({
        q: query,
        lat,
        lng,
        radiusMeters: radiusMeters ?? null,
        pageSize: 20,
      });
      void preferences
        .trackEvent(ctx.user.id, {
          type: "SEARCH",
          query,
          latitude: lat,
          longitude: lng,
          categoryId: result.offers[0]?.categoryId ?? null,
        })
        .catch(() => undefined);
      const minQty = Math.max(1, num(args, "minQuantity", 1) ?? 1);
      const offers = result.offers.filter((o) => o.quantity >= minQty).slice(0, 10);
      if (!offers.length) {
        return {
          success: true,
          message: radiusMeters
            ? `No store within ${(radiusMeters / 1000).toFixed(1)} km has '${query}' in stock.`
            : `No shop has '${query}' in stock.`,
          data: { query, radiusMeters: radiusMeters ?? null, count: 0, offers: [] },
        };
      }
      return {
        success: true,
        data: { query, radiusMeters: radiusMeters ?? null, count: offers.length, totalCount: result.totalCount, offers },
      };
    },
  },
  {
    name: "SEARCH_BY_IMAGE",
    description:
      "Find in-stock products that match a photo the shopper already uploaded (pass mediaId from the app). Uses vision then shop search.",
    roles: ["CONSUMER"],
    parameters: {
      type: "object",
      properties: {
        mediaId: { type: "string", description: "Uploaded media asset id" },
        latitude: { type: "number" },
        longitude: { type: "number" },
        radiusMeters: { type: "integer" },
      },
      required: ["mediaId"],
    },
    async execute(args, ctx) {
      const mediaId = str(args, "mediaId");
      const { lat, lng } = requireLocation(ctx, args);
      const radiusMeters = num(args, "radiusMeters");
      const result = await search.searchByImage({
        mediaId,
        lat,
        lng,
        radiusMeters: radiusMeters ?? null,
        pageSize: 20,
      });
      void preferences
        .trackEvent(ctx.user.id, {
          type: "SEARCH",
          query: result.query,
          categoryId: result.offers[0]?.categoryId ?? null,
          latitude: lat,
          longitude: lng,
          payload: { mediaId, vision: result.visionDescription },
        })
        .catch(() => undefined);
      return {
        success: true,
        message: result.visionDescription,
        data: {
          mediaId,
          description: result.visionDescription,
          queries: result.queries,
          count: result.offers.length,
          totalCount: result.totalCount,
          offers: result.offers.slice(0, 10),
        },
      };
    },
  },
  {
    name: "LIST_CATEGORIES",
    description: "List product categories shoppers can browse (staples, beverages, etc.).",
    roles: ["CONSUMER", "MERCHANT"],
    parameters: { type: "object", properties: {} },
    async execute() {
      const categories = await catalog.listCategories();
      return { success: true, data: { count: categories.length, categories } };
    },
  },
  {
    name: "SEARCH_BY_CATEGORY",
    description: "Find in-stock offers in a product category, nearest first. Pass categorySlug (e.g. beverages) or categoryId.",
    roles: ["CONSUMER"],
    parameters: {
      type: "object",
      properties: {
        categorySlug: { type: "string" },
        categoryId: { type: "string" },
        latitude: { type: "number" },
        longitude: { type: "number" },
        radiusMeters: { type: "integer" },
      },
      required: [],
    },
    async execute(args, ctx) {
      const { lat, lng } = requireLocation(ctx, args);
      const categorySlug = typeof args.categorySlug === "string" ? args.categorySlug.trim() : null;
      const categoryId = typeof args.categoryId === "string" ? args.categoryId.trim() : null;
      if (!categorySlug && !categoryId) {
        return { success: false, errorCode: "VALIDATION_FAILED", message: "categorySlug or categoryId required" };
      }
      const radiusMeters = num(args, "radiusMeters");
      const result = await search.search({
        lat,
        lng,
        categorySlug,
        categoryId,
        radiusMeters: radiusMeters ?? null,
        pageSize: 20,
      });
      void preferences
        .trackEvent(ctx.user.id, {
          type: "SEARCH",
          query: categorySlug || categoryId,
          categoryId: categoryId || result.offers[0]?.categoryId || null,
          latitude: lat,
          longitude: lng,
        })
        .catch(() => undefined);
      return {
        success: true,
        data: {
          categorySlug,
          categoryId,
          count: result.offers.length,
          totalCount: result.totalCount,
          offers: result.offers.slice(0, 10),
        },
      };
    },
  },
  {
    name: "COMPARE_PRICES",
    description: "Compare prices for a product across shops that have it in stock, cheapest first.",
    roles: ["CONSUMER"],
    parameters: {
      type: "object",
      properties: {
        query: { type: "string" },
        radiusMeters: { type: "integer" },
        latitude: { type: "number" },
        longitude: { type: "number" },
      },
      required: ["query"],
    },
    async execute(args, ctx) {
      const query = str(args, "query");
      const { lat, lng } = requireLocation(ctx, args);
      const radiusMeters = num(args, "radiusMeters");
      const result = await search.search({
        q: query,
        lat,
        lng,
        radiusMeters: radiusMeters ?? null,
        pageSize: 50,
      });
      const offers = [...result.offers].sort((a, b) => a.unitPriceKobo - b.unitPriceKobo).slice(0, 10);
      return {
        success: true,
        data: { query, radiusMeters: radiusMeters ?? null, count: offers.length, offers },
      };
    },
  },
  {
    name: "FIND_ALTERNATIVE_STORES",
    description: "Find other stores that stock a product, optionally excluding one store.",
    roles: ["CONSUMER"],
    parameters: {
      type: "object",
      properties: {
        productId: { type: "string" },
        excludeStoreId: { type: "string" },
        query: { type: "string", description: "Fallback product name if productId unknown" },
        radiusMeters: { type: "integer" },
        latitude: { type: "number" },
        longitude: { type: "number" },
      },
      required: [],
    },
    async execute(args, ctx) {
      const { lat, lng } = requireLocation(ctx, args);
      const radiusMeters = num(args, "radiusMeters");
      const q = typeof args.query === "string" && args.query.trim() ? args.query.trim() : "product";
      const result = await search.search({
        q,
        lat,
        lng,
        radiusMeters: radiusMeters ?? null,
        pageSize: 50,
      });
      const productId = typeof args.productId === "string" ? args.productId : null;
      const exclude = typeof args.excludeStoreId === "string" ? args.excludeStoreId : null;
      const alternatives = result.offers
        .filter((o) => (!productId || o.productId === productId) && (!exclude || o.storeId !== exclude))
        .slice(0, 10);
      return { success: true, data: { count: alternatives.length, alternatives } };
    },
  },
  {
    name: "GET_STORE_DETAILS",
    description: "Get a store's details and optionally whether a product query is in stock there.",
    roles: ["CONSUMER"],
    parameters: {
      type: "object",
      properties: {
        storeId: { type: "string" },
        query: { type: "string" },
      },
      required: ["storeId"],
    },
    async execute(args) {
      const store = await stores.get(str(args, "storeId"));
      let matching: unknown[] = [];
      if (typeof args.query === "string" && args.query.trim()) {
        const around = await search.search({
          q: args.query.trim(),
          lat: store.latitude,
          lng: store.longitude,
          radiusMeters: 200,
        });
        matching = around.offers.filter((o) => o.storeId === store.id);
      }
      return {
        success: true,
        data: {
          store: {
            id: store.id,
            name: store.name,
            address: store.address,
            openNow: store.openNow,
            openAt: store.openAt,
            closeAt: store.closeAt,
            phone: store.phone,
            category: store.category,
          },
          matchingOffers: matching,
        },
      };
    },
  },
  {
    name: "PLACE_ORDER",
    description: "Place an order at a store after the user confirmed. Debits the wallet into escrow.",
    roles: ["CONSUMER"],
    parameters: {
      type: "object",
      properties: {
        storeId: { type: "string" },
        items: {
          type: "array",
          items: {
            type: "object",
            properties: {
              productId: { type: "string" },
              quantity: { type: "integer" },
            },
            required: ["productId", "quantity"],
          },
        },
      },
      required: ["storeId", "items"],
    },
    async execute(args, ctx) {
      const storeId = str(args, "storeId");
      const items = args.items as { productId: string; quantity: number }[];
      if (!Array.isArray(items) || !items.length) {
        return { success: false, errorCode: "VALIDATION_FAILED", message: "Items required" };
      }
      const normalized = items.map((item) => ({
        productId: String(item.productId),
        quantity: Math.max(1, Math.floor(Number(item.quantity) || 1)),
      }));
      const order = await orders.place(ctx.user, { storeId, items: normalized }, emit);
      return {
        success: true,
        message: `Order placed for ₦${order.total}`,
        data: {
          orderId: order.id,
          total: order.total,
          totalKobo: order.totalKobo,
          ledgerHash: order.ledgerHash,
          status: order.status,
          items: order.items,
        },
      };
    },
  },
  {
    name: "PLACE_CART_ORDER",
    description:
      "Place an order for the shopper's current app cart (storeId + productIds from client context). Confirm with the user first.",
    roles: ["CONSUMER"],
    parameters: { type: "object", properties: {} },
    async execute(_args, ctx) {
      const cart = ctx.cart;
      if (!cart?.storeId || !cart.items?.length) {
        return {
          success: false,
          errorCode: "VALIDATION_FAILED",
          message: "Cart is empty or missing storeId/productIds — ask them to add items or use PLACE_ORDER",
        };
      }
      const items = cart.items
        .filter((i) => i.productId)
        .map((i) => ({
          productId: String(i.productId),
          quantity: Math.max(1, Math.floor(Number(i.quantity) || 1)),
        }));
      if (!items.length) {
        return {
          success: false,
          errorCode: "VALIDATION_FAILED",
          message: "Cart items are missing productIds",
        };
      }
      const order = await orders.place(ctx.user, { storeId: cart.storeId, items }, emit);
      return {
        success: true,
        message: `Order placed for ₦${order.total}`,
        data: {
          orderId: order.id,
          total: order.total,
          totalKobo: order.totalKobo,
          ledgerHash: order.ledgerHash,
          status: order.status,
          items: order.items,
        },
      };
    },
  },
  {
    name: "GET_MY_ORDERS",
    description: "List the shopper's recent orders.",
    roles: ["CONSUMER"],
    parameters: { type: "object", properties: {} },
    async execute(_args, ctx) {
      const list = await orders.mine(ctx.user);
      return { success: true, data: { count: list.length, orders: list.slice(0, 20) } };
    },
  },
  {
    name: "GET_MY_WALLET",
    description: "Get the signed-in user's demo wallet balance (and merchant escrow summary if merchant).",
    roles: ["CONSUMER", "MERCHANT"],
    parameters: { type: "object", properties: {} },
    async execute(_args, ctx) {
      const wallet = await payments.wallet(ctx.user);
      if (ctx.user.role === "MERCHANT") {
        const summary = await payments.merchantSummary(ctx.user);
        return { success: true, data: { wallet, summary } };
      }
      const escrow = await payments.consumerSummary(ctx.user);
      return { success: true, data: { wallet, escrow } };
    },
  },
  {
    name: "FUND_WALLET",
    description:
      "Add demo funds to the shopper's wallet when they ask (or when PLACE_ORDER fails for insufficient balance). Amount in kobo (₦1 = 100 kobo).",
    roles: ["CONSUMER"],
    parameters: {
      type: "object",
      properties: {
        amountKobo: {
          type: "integer",
          description: "Amount in kobo. Default 500000 (₦5,000). Max 5_000_000.",
        },
      },
      required: [],
    },
    async execute(args, ctx) {
      const amountKobo = Math.min(5_000_000, Math.max(100, Math.floor(num(args, "amountKobo", 500_000) ?? 500_000)));
      const wallet = await payments.fundDemo(ctx.user, amountKobo);
      return {
        success: true,
        message: `Added ₦${(amountKobo / 100).toFixed(2)}. New balance ₦${wallet.balance}`,
        data: wallet,
      };
    },
  },
  {
    name: "SUBMIT_PRODUCT_REVIEW",
    description:
      "Submit or update a 1–5 star review for a product the shopper has ordered. Ask for rating (and optional comment) first.",
    roles: ["CONSUMER"],
    parameters: {
      type: "object",
      properties: {
        productId: { type: "string" },
        rating: { type: "integer", description: "1 to 5 stars" },
        comment: { type: "string" },
        orderId: { type: "string", description: "Optional order that included the product" },
      },
      required: ["productId", "rating"],
    },
    async execute(args, ctx) {
      const review = await reviews.create(ctx.user, {
        productId: str(args, "productId"),
        rating: num(args, "rating") ?? 0,
        comment: typeof args.comment === "string" ? args.comment : null,
        orderId: typeof args.orderId === "string" ? args.orderId : null,
      });
      return {
        success: true,
        message: `Saved ${review.rating}-star review for ${review.productName ?? "the product"}`,
        data: review,
      };
    },
  },
  {
    name: "GET_PRODUCT_REVIEWS",
    description: "Read recent reviews and the average rating for a product.",
    roles: ["CONSUMER", "MERCHANT"],
    parameters: {
      type: "object",
      properties: {
        productId: { type: "string" },
        limit: { type: "integer" },
      },
      required: ["productId"],
    },
    async execute(args) {
      const summary = await reviews.forProduct(str(args, "productId"), num(args, "limit", 10) ?? 10);
      return { success: true, data: summary };
    },
  },
  {
    name: "GET_SHOPPER_PROFILE",
    description: "Load this shopper's preferences, recent searches, and AI shopper review for personalised suggestions.",
    roles: ["CONSUMER"],
    parameters: { type: "object", properties: {} },
    async execute(_args, ctx) {
      const profile = await preferences.getProfile(ctx.user);
      return { success: true, data: profile };
    },
  },
  {
    name: "REFRESH_SHOPPER_REVIEW",
    description: "Regenerate the AI written shopper review from their recent behaviour. Confirm with the user first.",
    roles: ["CONSUMER"],
    parameters: { type: "object", properties: {} },
    async execute(_args, ctx) {
      const profile = await preferences.generateAiReview(ctx.user);
      return { success: true, message: "Shopper review updated", data: profile };
    },
  },
  {
    name: "GET_MY_INVENTORY",
    description: "List inventory for one of the merchant's stores.",
    roles: ["MERCHANT"],
    parameters: {
      type: "object",
      properties: { storeId: { type: "string", description: "Optional; defaults to first owned store" } },
    },
    async execute(args, ctx) {
      const mine = await stores.mine(ctx.user);
      if (!mine.length) return { success: false, message: "You have no shops yet.", errorCode: "NOT_FOUND" };
      const storeId = typeof args.storeId === "string" && args.storeId ? args.storeId : mine[0]!.id;
      const stock = await inventory.list(ctx.user, storeId);
      return { success: true, data: { storeId, storeName: mine.find((s) => s.id === storeId)?.name, stock } };
    },
  },
  {
    name: "GET_LOW_STOCK_ITEMS",
    description: "List low or out-of-stock items across the merchant's shops.",
    roles: ["MERCHANT"],
    parameters: {
      type: "object",
      properties: { threshold: { type: "integer", description: "Treat quantity at or below this as low. Default 5." } },
    },
    async execute(args, ctx) {
      const threshold = Math.max(0, num(args, "threshold", 5) ?? 5);
      const mine = await stores.mine(ctx.user);
      const low = [];
      for (const store of mine) {
        const stock = await inventory.list(ctx.user, store.id);
        for (const item of stock) {
          if (item.quantity <= threshold) {
            low.push({
              storeId: store.id,
              storeName: store.name,
              productId: item.productId,
              quantity: item.quantity,
              unitPriceKobo: item.unitPriceKobo,
              suggestedRestock: Math.max(10, threshold * 2 - item.quantity),
            });
          }
        }
      }
      return { success: true, data: { threshold, count: low.length, items: low } };
    },
  },
  {
    name: "GET_INCOMING_ORDERS",
    description: "List recent orders for the merchant's shops.",
    roles: ["MERCHANT"],
    parameters: { type: "object", properties: {} },
    async execute(_args, ctx) {
      const list = await orders.merchantOrders(ctx.user);
      return { success: true, data: { count: list.length, orders: list.slice(0, 30) } };
    },
  },
  {
    name: "UPDATE_STOCK",
    description: "Set a product's shelf quantity (and optional price) after the merchant confirms.",
    roles: ["MERCHANT"],
    parameters: {
      type: "object",
      properties: {
        storeId: { type: "string" },
        productId: { type: "string" },
        quantity: { type: "integer" },
        unitPriceKobo: { type: "integer" },
      },
      required: ["storeId", "productId", "quantity"],
    },
    async execute(args, ctx) {
      const storeId = str(args, "storeId");
      const productId = str(args, "productId");
      const quantity = num(args, "quantity");
      if (quantity == null || quantity < 0) {
        return { success: false, errorCode: "VALIDATION_FAILED", message: "quantity must be >= 0" };
      }
      let unitPriceKobo = num(args, "unitPriceKobo");
      if (unitPriceKobo == null) {
        const existing = await inventory.list(ctx.user, storeId);
        unitPriceKobo = existing.find((i) => i.productId === productId)?.unitPriceKobo ?? 0;
      }
      const item = await inventory.upsert(ctx.user, storeId, {
        productId,
        quantity,
        unitPriceKobo,
      });
      return { success: true, message: `Stock updated to ${quantity}`, data: item };
    },
  },
];

export function definitionsFor(role: AuthUser["role"]): ToolSpec[] {
  return TOOLS.filter((t) => t.roles.includes(role)).map((t) => ({
    type: "function" as const,
    function: {
      name: t.name,
      description: t.description,
      parameters: t.parameters,
    },
  }));
}

export async function invokeTool(
  name: string,
  rawArgs: string,
  ctx: ToolContext,
): Promise<{ tool: string; arguments: Record<string, unknown>; result: ToolResult }> {
  const tool = TOOLS.find((t) => t.name === name && t.roles.includes(ctx.user.role));
  let args: Record<string, unknown> = {};
  try {
    args = rawArgs ? (JSON.parse(rawArgs) as Record<string, unknown>) : {};
  } catch {
    return {
      tool: name,
      arguments: {},
      result: { success: false, errorCode: "INVALID_ARGUMENT", message: "Tool arguments were not valid JSON" },
    };
  }
  if (!tool) {
    return {
      tool: name,
      arguments: args,
      result: { success: false, errorCode: "NOT_FOUND", message: `Unknown tool ${name}` },
    };
  }
  try {
    const result = await tool.execute(args, ctx);
    return { tool: name, arguments: args, result };
  } catch (e) {
    if (e instanceof ApiError) {
      return {
        tool: name,
        arguments: args,
        result: { success: false, errorCode: e.code, message: e.message },
      };
    }
    const err = e as Error & { code?: string };
    return {
      tool: name,
      arguments: args,
      result: {
        success: false,
        errorCode: err.code || "TOOL_FAILED",
        message: err.message || "Tool failed",
      },
    };
  }
}
