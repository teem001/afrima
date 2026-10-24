import type { AuthUser } from "../auth/jwt.js";
import { parseToken } from "../auth/jwt.js";
import { ApiError } from "../errors.js";
import * as auth from "../services/authService.js";
import * as catalog from "../services/catalogService.js";
import * as stores from "../services/storeService.js";
import * as inventory from "../services/inventoryService.js";
import * as media from "../services/mediaService.js";
import * as search from "../services/searchService.js";
import * as orders from "../services/orderService.js";
import * as payments from "../services/paymentService.js";
import * as twins from "../services/twinService.js";
import * as ai from "../services/ai/agent.js";
import * as reviews from "../services/reviewService.js";
import { emit } from "./hub.js";

function requireUser(user: AuthUser | null): AuthUser {
  if (!user) throw ApiError.unauthorized();
  return user;
}

export type DispatchSession = {
  user: AuthUser | null;
  setUser: (u: AuthUser | null) => void;
  requestId?: string | null;
  sendEvent?: (event: string, data: unknown) => void;
};

export async function dispatch(
  type: string,
  payload: Record<string, unknown> | undefined,
  session: DispatchSession,
): Promise<unknown> {
  switch (type) {
    case "ping":
      return "pong";

    case "auth.register": {
      const result = await auth.register(payload as never);
      session.setUser({ id: result.user.id, email: result.user.email, role: result.user.role });
      return result;
    }
    case "auth.login": {
      const result = await auth.login(payload as never);
      session.setUser({ id: result.user.id, email: result.user.email, role: result.user.role });
      return result;
    }
    case "auth.resume": {
      const token = String((payload as { token?: string })?.token || "");
      const parsed = await parseToken(token);
      if (!parsed) throw ApiError.unauthorized("Invalid token");
      session.setUser(parsed);
      return auth.me(parsed);
    }
    case "auth.logout":
      session.setUser(null);
      return null;
    case "me":
      return auth.me(requireUser(session.user));

    case "search":
      return search.search(payload as never);
    case "catalog.products":
      return catalog.listProducts();
    case "catalog.products.create":
      return catalog.createProduct(requireUser(session.user), payload as never);

    case "stores.list":
      return stores.listActive();
    case "stores.mine":
      return stores.mine(requireUser(session.user));
    case "stores.get":
      return stores.get(String((payload as { storeId: string }).storeId));
    case "stores.create":
      return stores.create(requireUser(session.user), payload as never);
    case "stores.update": {
      const body = payload as { storeId: string } & Record<string, unknown>;
      const { storeId, ...rest } = body;
      return stores.update(requireUser(session.user), storeId, rest as never);
    }

    case "inventory.list":
      return inventory.list(requireUser(session.user), String((payload as { storeId: string }).storeId));
    case "inventory.upsert": {
      const body = payload as { storeId: string; productId: string; quantity: number; unitPriceKobo: number };
      return inventory.upsert(requireUser(session.user), body.storeId, body);
    }
    case "inventory.restock": {
      const body = payload as {
        storeId: string;
        productId: string;
        addQuantity: number;
        unitPriceKobo?: number | null;
      };
      return inventory.restock(requireUser(session.user), body.storeId, body);
    }

    case "media.upload":
      return media.upload(requireUser(session.user), payload as never);

    case "orders.place":
      return orders.place(requireUser(session.user), payload as never, emit);
    case "orders.mine":
      return orders.mine(requireUser(session.user));
    case "orders.get":
      return orders.get(requireUser(session.user), String((payload as { orderId: string }).orderId));
    case "orders.merchant":
      return orders.merchantOrders(requireUser(session.user));
    case "orders.accept":
      return orders.accept(requireUser(session.user), String((payload as { orderId: string }).orderId), emit);
    case "orders.fulfil":
      return orders.fulfil(requireUser(session.user), String((payload as { orderId: string }).orderId), emit);
    case "orders.cancel":
      return orders.cancel(requireUser(session.user), String((payload as { orderId: string }).orderId), emit);
    case "verify":
      return orders.verify(String((payload as { orderId: string }).orderId));

    case "wallet.me":
      return payments.wallet(requireUser(session.user));
    case "wallet.fund":
      return payments.fundDemo(requireUser(session.user), Number((payload as { amountKobo: number }).amountKobo));
    case "wallet.transactions":
      return payments.transactions(requireUser(session.user));
    case "payments.order":
      return payments.orderPayment(String((payload as { orderId: string }).orderId));
    case "payments.merchantSummary":
      return payments.merchantSummary(requireUser(session.user));
    case "payouts.request":
      return payments.requestPayout(requireUser(session.user), Number((payload as { amountKobo: number }).amountKobo));
    case "payouts.mine":
      return payments.myPayouts(requireUser(session.user));

    case "twin.shop":
      return twins.shopTwin(requireUser(session.user), String((payload as { storeId: string }).storeId));
    case "twin.me":
      return twins.myTwin(requireUser(session.user));
    case "twin.simulate":
      return twins.simulate(requireUser(session.user), payload as never);

    case "reviews.create":
      return reviews.create(requireUser(session.user), payload as never);
    case "reviews.forProduct":
      return reviews.forProduct(String((payload as { productId: string }).productId));
    case "reviews.forOrder":
      return reviews.forOrder(
        requireUser(session.user),
        String((payload as { orderId: string }).orderId),
      );

    case "ai.status":
      return ai.status();
    case "ai.chat": {
      const user = requireUser(session.user);
      const requestId = session.requestId || "ai";
      return ai.chat(
        user,
        {
          conversationId: payload?.conversationId ? String(payload.conversationId) : undefined,
          message: String(payload?.message ?? ""),
          latitude: payload?.latitude != null ? Number(payload.latitude) : undefined,
          longitude: payload?.longitude != null ? Number(payload.longitude) : undefined,
          context: (payload?.context as import("../services/ai/prompts.js").AiClientContext) || undefined,
        },
        requestId,
        (progress) => session.sendEvent?.("ai.progress", progress),
      );
    }

    case "anchors.pending":
      return null;

    default:
      throw ApiError.badRequest("VALIDATION_FAILED", `Unknown message type: ${type}`);
  }
}
