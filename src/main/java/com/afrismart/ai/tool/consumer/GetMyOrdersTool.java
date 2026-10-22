package com.afrismart.ai.tool.consumer;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.afrismart.ai.tool.AiTool;
import com.afrismart.ai.tool.AiToolContext;
import com.afrismart.ai.tool.AiToolType;
import com.afrismart.ai.tool.JsonSchema;
import com.afrismart.ai.tool.OrderViews;
import com.afrismart.ai.tool.ToolArgs;
import com.afrismart.ai.tool.ToolResult;
import com.afrismart.orders.OrderService;
import com.afrismart.shared.security.Role;

@Component
public class GetMyOrdersTool implements AiTool {

    private final OrderService orderService;
    private final OrderViews orderViews;

    public GetMyOrdersTool(OrderService orderService, OrderViews orderViews) {
        this.orderService = orderService;
        this.orderViews = orderViews;
    }

    @Override
    public AiToolType type() {
        return AiToolType.GET_MY_ORDERS;
    }

    @Override
    public String description() {
        return "List the user's most recent orders with store, status, items, and total.";
    }

    @Override
    public JsonSchema parameters() {
        return JsonSchema.object(Map.of(
                "limit", JsonSchema.integer("How many orders to return, newest first. Default 5, max 20.")
        ));
    }

    @Override
    public Set<Role> roles() {
        return Set.of(Role.CONSUMER);
    }

    @Override
    public ToolResult execute(ToolArgs args, AiToolContext context) {
        int limit = Math.max(1, Math.min(20, args.integer("limit", 5)));
        List<OrderViews.OrderView> orders = orderViews.assemble(
                orderService.listMine(context.user()).stream().limit(limit).toList()
        );
        if (orders.isEmpty()) {
            return ToolResult.ok("The user has no orders yet.", new Orders(orders));
        }
        return ToolResult.ok(new Orders(orders));
    }

    public record Orders(List<OrderViews.OrderView> orders) {
    }
}
