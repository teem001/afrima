package com.afrismart.ai.tool.merchant;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.afrismart.ai.tool.AiTool;
import com.afrismart.ai.tool.AiToolContext;
import com.afrismart.ai.tool.AiToolType;
import com.afrismart.ai.tool.JsonSchema;
import com.afrismart.ai.tool.OrderViews;
import com.afrismart.ai.tool.ToolArgs;
import com.afrismart.ai.tool.ToolException;
import com.afrismart.ai.tool.ToolResult;
import com.afrismart.orders.OrderService;
import com.afrismart.shared.security.Role;

@Component
public class GetIncomingOrdersTool implements AiTool {

    private static final List<String> STATUSES = List.of("PLACED", "ACCEPTED", "CANCELLED", "ALL");

    private final OrderService orderService;
    private final OrderViews orderViews;

    public GetIncomingOrdersTool(OrderService orderService, OrderViews orderViews) {
        this.orderService = orderService;
        this.orderViews = orderViews;
    }

    @Override
    public AiToolType type() {
        return AiToolType.GET_INCOMING_ORDERS;
    }

    @Override
    public String description() {
        return "List orders placed at the merchant's stores, newest first. Defaults to orders waiting to be accepted.";
    }

    @Override
    public JsonSchema parameters() {
        return JsonSchema.object(Map.of(
                "status", JsonSchema.oneOf("Which orders to show. Default PLACED.", STATUSES),
                "limit", JsonSchema.integer("How many orders to return. Default 10, max 50.")
        ));
    }

    @Override
    public Set<Role> roles() {
        return Set.of(Role.MERCHANT);
    }

    @Override
    public ToolResult execute(ToolArgs args, AiToolContext context) {
        String status = args.string("status");
        status = status == null ? "PLACED" : status.toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(status)) {
            throw ToolException.invalidArgument("status must be one of " + STATUSES);
        }
        String wanted = status;
        int limit = Math.max(1, Math.min(50, args.integer("limit", 10)));
        List<OrderViews.OrderView> orders = orderViews.assemble(orderService.listForMerchant(context.user()).stream()
                .filter(order -> wanted.equals("ALL") || order.status().name().equals(wanted))
                .limit(limit)
                .toList());
        Incoming result = new Incoming(wanted, orders.size(), orders);
        if (orders.isEmpty()) {
            return ToolResult.ok("No " + wanted.toLowerCase(Locale.ROOT) + " orders.", result);
        }
        return ToolResult.ok(result);
    }

    public record Incoming(String status, int count, List<OrderViews.OrderView> orders) {
    }
}
