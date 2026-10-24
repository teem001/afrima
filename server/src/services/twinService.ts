import type { AuthUser } from "../auth/jwt.js";
import { ApiError } from "../errors.js";
import { formatNaira } from "../money.js";
import * as auth from "./authService.js";
import * as payments from "./paymentService.js";
import * as inventory from "./inventoryService.js";
import * as stores from "./storeService.js";
import * as orders from "./orderService.js";

export async function shopTwin(user: AuthUser, storeId: string) {
  const store = await stores.get(storeId);
  if (store.merchantId !== user.id) throw ApiError.forbidden();
  const stock = await inventory.list(user, storeId);
  const money = await payments.merchantSummary(user);
  const suggestions = stock
    .filter((s) => s.quantity <= 5)
    .map((s) => ({
      type: s.quantity === 0 ? "OUT_OF_STOCK" : "LOW_STOCK",
      productId: s.productId,
      message: s.quantity === 0 ? "Restock this item" : "Running low — consider restocking",
      severity: s.quantity === 0 ? "HIGH" : "MEDIUM",
    }));
  return {
    storeId: store.id,
    storeName: store.name,
    address: store.address,
    openNow: store.openNow,
    imageUrl: store.imageUrl ?? null,
    stock: stock.map((s) => ({
      productId: s.productId,
      productName: "",
      sku: "",
      imageUrl: null as string | null,
      quantity: s.quantity,
      unitPriceKobo: s.unitPriceKobo,
      unitPrice: s.unitPrice,
      unitsSold7d: 0,
      velocityPerDay: 0,
      daysToStockout: null,
      lowStock: s.quantity <= 5,
    })),
    suggestions: suggestions.map((s) => ({
      type: s.type,
      message: s.message,
      productId: s.productId,
      suggestedQty: null,
    })),
    money,
  };
}

export async function myTwin(user: AuthUser) {
  if (user.role !== "CONSUMER") throw ApiError.forbidden();
  const me = await auth.me(user);
  const wallet = await payments.wallet(user);
  const escrow = await payments.consumerSummary(user);
  const recent = await orders.mine(user);
  const totalSpentKobo = recent
    .filter((o) => o.status !== "CANCELLED")
    .reduce((sum, o) => sum + Number(o.totalKobo), 0);
  return {
    userId: user.id,
    email: user.email,
    fullName: me.fullName,
    role: user.role,
    wallet,
    escrow,
    ordersCount: recent.length,
    totalSpentKobo,
    totalSpent: formatNaira(totalSpentKobo),
    recentOrders: recent.slice(0, 5),
  };
}

export async function simulate(
  user: AuthUser,
  input: { storeId: string; productId: string; restockQty?: number; priceChangePercent?: number },
) {
  const stock = await inventory.list(user, input.storeId);
  const item = stock.find((s) => s.productId === input.productId);
  if (!item) throw ApiError.notFound("Inventory item not found");
  const restock = input.restockQty ?? 0;
  const pct = input.priceChangePercent ?? 0;
  const simulatedPriceKobo = Math.max(0, Math.round(item.unitPriceKobo * (1 + pct / 100)));
  const afterQty = item.quantity + restock;
  return {
    productId: input.productId,
    productName: "",
    currentQuantity: item.quantity,
    restockQty: restock,
    currentPriceKobo: item.unitPriceKobo,
    currentPrice: item.unitPrice,
    simulatedPriceKobo,
    simulatedPrice: formatNaira(simulatedPriceKobo),
    velocityPerDay: 0,
    daysToStockoutNow: item.quantity === 0 ? 0 : null,
    daysToStockoutAfterRestock: afterQty === 0 ? 0 : null,
    projectedRevenue7dKobo: item.unitPriceKobo * Math.min(item.quantity, 7),
    projectedRevenue7d: formatNaira(item.unitPriceKobo * Math.min(item.quantity, 7)),
    simulatedRevenue7dKobo: simulatedPriceKobo * Math.min(afterQty, 7),
    simulatedRevenue7d: formatNaira(simulatedPriceKobo * Math.min(afterQty, 7)),
    note: "Lightweight simulation — velocity model not yet applied on Node backend.",
  };
}
