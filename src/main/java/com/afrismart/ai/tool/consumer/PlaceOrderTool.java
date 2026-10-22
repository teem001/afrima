package com.afrismart.ai.tool.consumer;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.afrismart.ai.tool.AiTool;
import com.afrismart.ai.tool.AiToolContext;
import com.afrismart.ai.tool.AiToolType;
import com.afrismart.ai.tool.JsonSchema;
import com.afrismart.ai.tool.OrderViews;
import com.afrismart.ai.tool.ToolArgs;
import com.afrismart.ai.tool.ToolException;
import com.afrismart.ai.tool.ToolResult;
import com.afrismart.orders.OrderResponse;
import com.afrismart.orders.OrderService;
import com.afrismart.orders.PlaceOrderRequest;
import com.afrismart.shared.security.Role;

@Component
public class PlaceOrderTool implements AiTool {

    private final OrderService orderService;
    private final OrderViews orderViews;

    public PlaceOrderTool(OrderService orderService, OrderViews orderViews) {
        this.orderService = orderService;
        this.orderViews = orderViews;
    }

    @Override
    public AiToolType type() {
        return AiToolType.PLACE_ORDER;
    }

    @Override
    public String description() {
        return "Place an order at one store. Only call after the user has explicitly confirmed the store, items, "
                + "quantities, and total in their latest message. Reserves stock and returns the order with its ledger hash.";
    }

    @Override
    public JsonSchema parameters() {
        return JsonSchema.object(Map.of(
                "storeId", JsonSchema.string("Store id from an earlier tool result"),
                "items", JsonSchema.array("Products to buy", JsonSchema.object(Map.of(
                        "productId", JsonSchema.string("Product id from an earlier tool result"),
                        "quantity", JsonSchema.integer("Units to buy, at least 1")
                ), "productId", "quantity"))
        ), "storeId", "items");
    }

    @Override
    public Set<Role> roles() {
        return Set.of(Role.CONSUMER);
    }

    @Override
    public ToolResult execute(ToolArgs args, AiToolContext context) {
        UUID storeId = args.requireUuid("storeId");
        List<ToolArgs> rawItems = args.objects("items");
        if (rawItems.isEmpty()) {
            throw ToolException.invalidArgument("items must contain at least one product");
        }
        List<PlaceOrderRequest.Item> items = rawItems.stream().map(item -> {
            int quantity = item.integer("quantity", 0);
            if (quantity < 1) {
                throw ToolException.invalidArgument("quantity must be at least 1");
            }
            return new PlaceOrderRequest.Item(item.requireUuid("productId"), quantity);
        }).toList();

        OrderResponse order = orderService.place(context.user(), new PlaceOrderRequest(storeId, items));
        return ToolResult.ok("Order placed", orderViews.assemble(List.of(order)).getFirst());
    }
}
