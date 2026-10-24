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
import * as preferences from "../services/preferenceService.js";
import * as notifications from "../services/notificationService.js";
import { emit } from "./hub.js";
function requireUser(user) {
    if (!user)
        throw ApiError.unauthorized();
    return user;
}
export async function dispatch(type, payload, session) {
    switch (type) {
        case "ping":
            return "pong";
        case "auth.register": {
            const result = await auth.register(payload);
            session.setUser({ id: result.user.id, email: result.user.email, role: result.user.role });
            return result;
        }
        case "auth.login": {
            const result = await auth.login(payload);
            session.setUser({ id: result.user.id, email: result.user.email, role: result.user.role });
            return result;
        }
        case "auth.resume": {
            const token = String(payload?.token || "");
            const parsed = await parseToken(token);
            if (!parsed)
                throw ApiError.unauthorized("Invalid token");
            session.setUser(parsed);
            return auth.me(parsed);
        }
        case "auth.logout":
            session.setUser(null);
            return null;
        case "me":
            return auth.me(requireUser(session.user));
        case "search": {
            const result = await search.search(payload);
            if (session.user?.role === "CONSUMER") {
                const body = payload;
                void preferences
                    .trackEvent(session.user.id, {
                    type: "SEARCH",
                    query: body.q || body.categorySlug || null,
                    categoryId: body.categoryId || result.offers[0]?.categoryId || null,
                    latitude: body.lat ?? null,
                    longitude: body.lng ?? null,
                })
                    .catch(() => undefined);
            }
            return result;
        }
        case "catalog.products":
            return catalog.listProducts();
        case "catalog.categories":
            return catalog.listCategories();
        case "catalog.products.create":
            return catalog.createProduct(requireUser(session.user), payload);
        case "stores.list":
            return stores.listActive();
        case "stores.mine":
            return stores.mine(requireUser(session.user));
        case "stores.get":
            return stores.get(String(payload.storeId));
        case "stores.create":
            return stores.create(requireUser(session.user), payload);
        case "stores.update": {
            const body = payload;
            const { storeId, ...rest } = body;
            return stores.update(requireUser(session.user), storeId, rest);
        }
        case "inventory.list":
            return inventory.list(requireUser(session.user), String(payload.storeId));
        case "inventory.upsert": {
            const body = payload;
            return inventory.upsert(requireUser(session.user), body.storeId, body);
        }
        case "inventory.restock": {
            const body = payload;
            return inventory.restock(requireUser(session.user), body.storeId, body);
        }
        case "media.upload":
            return media.upload(requireUser(session.user), payload);
        case "orders.place":
            return orders.place(requireUser(session.user), payload, emit);
        case "orders.mine":
            return orders.mine(requireUser(session.user));
        case "orders.get":
            return orders.get(requireUser(session.user), String(payload.orderId));
        case "orders.merchant":
            return orders.merchantOrders(requireUser(session.user));
        case "orders.accept":
            return orders.accept(requireUser(session.user), String(payload.orderId), emit);
        case "orders.fulfil":
            return orders.fulfil(requireUser(session.user), String(payload.orderId), emit);
        case "orders.cancel":
            return orders.cancel(requireUser(session.user), String(payload.orderId), emit);
        case "verify":
            return orders.verify(String(payload.orderId));
        case "wallet.me":
            return payments.wallet(requireUser(session.user));
        case "wallet.fund":
            return payments.fundDemo(requireUser(session.user), Number(payload.amountKobo));
        case "wallet.transactions":
            return payments.transactions(requireUser(session.user));
        case "payments.order":
            return payments.orderPayment(String(payload.orderId));
        case "payments.merchantSummary":
            return payments.merchantSummary(requireUser(session.user));
        case "payouts.request":
            return payments.requestPayout(requireUser(session.user), Number(payload.amountKobo));
        case "payouts.mine":
            return payments.myPayouts(requireUser(session.user));
        case "twin.shop":
            return twins.shopTwin(requireUser(session.user), String(payload.storeId));
        case "twin.me":
            return twins.myTwin(requireUser(session.user));
        case "twin.simulate":
            return twins.simulate(requireUser(session.user), payload);
        case "reviews.create":
            return reviews.create(requireUser(session.user), payload);
        case "reviews.forProduct":
            return reviews.forProduct(String(payload.productId));
        case "reviews.forOrder":
            return reviews.forOrder(requireUser(session.user), String(payload.orderId));
        case "prefs.me":
            return preferences.getProfile(requireUser(session.user));
        case "prefs.aiReview":
            return preferences.generateAiReview(requireUser(session.user));
        case "notifications.list":
            return notifications.list(requireUser(session.user));
        case "notifications.unreadCount":
            return notifications.unreadCount(requireUser(session.user));
        case "notifications.markRead":
            return notifications.markRead(requireUser(session.user), payload?.notificationId ?? null);
        case "notifications.subscriptions":
            return notifications.listSubscriptions(requireUser(session.user));
        case "notifications.subscribe":
            return notifications.upsertSubscription(requireUser(session.user), payload);
        case "ai.status":
            return ai.status();
        case "ai.chat": {
            const user = requireUser(session.user);
            const requestId = session.requestId || "ai";
            return ai.chat(user, {
                conversationId: payload?.conversationId ? String(payload.conversationId) : undefined,
                message: String(payload?.message ?? ""),
                latitude: payload?.latitude != null ? Number(payload.latitude) : undefined,
                longitude: payload?.longitude != null ? Number(payload.longitude) : undefined,
                context: payload?.context || undefined,
            }, requestId, (progress) => session.sendEvent?.("ai.progress", progress));
        }
        case "anchors.pending":
            return null;
        default:
            throw ApiError.badRequest("VALIDATION_FAILED", `Unknown message type: ${type}`);
    }
}
//# sourceMappingURL=router.js.map