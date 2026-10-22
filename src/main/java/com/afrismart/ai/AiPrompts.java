package com.afrismart.ai;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;

import com.afrismart.ai.tool.AiToolContext;
import com.afrismart.shared.config.AfriSmartProperties;
import com.afrismart.shared.security.Role;

@Component
public class  AiPrompts {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, HH:mm");

    private static final String COMMON_RULES = """
            Rules:
            - Every fact about stores, stock, prices, distances, and orders must come from a tool call in this \
            conversation. Never invent stores, products, ids, or prices.
            - Prices from tools are in Nigerian naira (e.g. "750.00"). Quote them with the naira sign: ₦750.
            - Ids are for tools only. Refer to stores and products by name when talking to the user.
            - If a tool returns an error, explain it plainly and suggest the next step.
            - Keep replies short: a few sentences or a compact list. No markdown tables.
            """;

    private static final String CONSUMER = """
            You are AfriSmart, a shopping assistant for a Lagos marketplace that connects shoppers to nearby stores.
            Help the user find products near them, compare prices, and place orders.

            How to work:
            - To find a product, call SEARCH_PRODUCTS_NEARBY. For "cheapest" or "best deal", call COMPARE_PRICES.
            - If the user names a preferred store, check it with GET_STORE_DETAILS (pass the product as `query`).
            - If that store is out of stock, closed, or cannot cover the quantity, do not stop there: call \
            FIND_ALTERNATIVE_STORES excluding that store and recommend the best alternative with its distance and price.
            - Before PLACE_ORDER, summarise store, items, quantities, and total, and ask the user to confirm. \
            Only call PLACE_ORDER after they clearly say yes in their latest message.
            - After placing an order, give the order total and the first 12 characters of its ledger hash as a receipt.
            - If a tool says the location is unknown, ask the user to share their location.
            """;

    private static final String MERCHANT = """
            You are AfriSmart Merchant Assistant, helping a Lagos store owner run their shop.
            Help them understand stock levels, spot items to restock, and handle incoming orders.

            How to work:
            - For stock questions, call GET_MY_INVENTORY. For "what should I restock", call GET_LOW_STOCK_ITEMS and \
            present the suggested restock quantities alongside recent sales.
            - For new or pending orders, call GET_INCOMING_ORDERS.
            - Before UPDATE_STOCK, repeat the store, product, new quantity, and price back to the merchant and ask \
            them to confirm. Only call UPDATE_STOCK after they clearly say yes in their latest message. \
            Quantity is the new total on the shelf, so add to the current level yourself if they say "add 20".
            """;

    private final AfriSmartProperties properties;

    public AiPrompts(AfriSmartProperties properties) {
        this.properties = properties;
    }

    public String systemPrompt(AiToolContext context) {
        return systemPrompt(context, null);
    }

    public String systemPrompt(AiToolContext context, AiClientContext client) {
        String persona = context.user().role() == Role.MERCHANT ? MERCHANT : CONSUMER;
        return persona + "\n" + COMMON_RULES + "\n" + session(context) + screen(client);
    }

    private String screen(AiClientContext client) {
        if (client == null) {
            return "";
        }
        StringBuilder out = new StringBuilder("App screen (from the user's device):\n");
        if (client.screen() != null) {
            out.append("- Screen: ").append(client.screen()).append('\n');
        }
        if (client.locationLabel() != null) {
            out.append("- Area: ").append(client.locationLabel()).append('\n');
        }
        if (client.radiusMeters() != null) {
            out.append("- Search radius: ").append(client.radiusMeters())
                    .append(" m. Pass it as radiusMeters unless the user asks for a different distance.\n");
        }
        if (client.storeName() != null) {
            out.append("- Store being viewed: ").append(client.storeName()).append('\n');
        }
        if (client.searchQuery() != null) {
            out.append("- The latest message was typed into the search bar. Treat it as a shopping request: find it ")
                    .append("near the user and recommend the best option (in stock, close, good price) in two or three ")
                    .append("sentences. Do not place orders from a search.\n");
        }
        AiClientContext.Cart cart = client.cart();
        if (cart != null && cart.items() != null && !cart.items().isEmpty()) {
            out.append("- Cart at ").append(cart.storeName() == null ? "a store" : cart.storeName()).append(": ");
            out.append(String.join(", ", cart.items().stream()
                    .map(line -> line.quantity() + "× " + line.productName() + " @ ₦" + line.unitPrice())
                    .toList()));
            out.append('\n');
        }
        return out.toString();
    }

    private String session(AiToolContext context) {
        ZonedDateTime now = ZonedDateTime.now(properties.getClock().zoneId());
        String location = context.latitude() != null && context.longitude() != null
                ? "Device location: " + context.latitude() + ", " + context.longitude() + " (tools use it automatically)."
                : "Device location: unknown.";
        return """
                Session:
                - User: %s (%s)
                - Local time: %s (%s)
                - %s
                """.formatted(
                context.user().email(),
                context.user().role().name().toLowerCase(),
                TIME.format(now),
                now.getZone().getId(),
                location
        );
    }
}
