package com.afrismart.ai.tool;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.afrismart.catalog.CatalogService;
import com.afrismart.catalog.ProductResponse;
import com.afrismart.orders.OrderResponse;
import com.afrismart.stores.StoreService;

@Component
public class OrderViews {

    private final StoreService storeService;
    private final CatalogService catalogService;

    public OrderViews(StoreService storeService, CatalogService catalogService) {
        this.storeService = storeService;
        this.catalogService = catalogService;
    }

    public List<OrderView> assemble(List<OrderResponse> orders) {
        Map<UUID, String> storeNames = new HashMap<>();
        Map<UUID, ProductResponse> products = catalogService.getAll(orders.stream()
                .flatMap(order -> order.items().stream())
                .map(OrderResponse.Line::productId)
                .toList());
        return orders.stream().map(order -> new OrderView(
                order.id(),
                order.storeId(),
                storeNames.computeIfAbsent(order.storeId(), id -> storeService.get(id).name()),
                order.status().name(),
                order.total(),
                order.totalKobo(),
                order.ledgerHash(),
                order.createdAt(),
                order.items().stream().map(line -> new LineView(
                        line.productId(),
                        products.containsKey(line.productId()) ? products.get(line.productId()).name() : null,
                        line.quantity(),
                        line.unitPrice()
                )).toList()
        )).toList();
    }

    public record OrderView(
            UUID orderId,
            UUID storeId,
            String storeName,
            String status,
            String total,
            long totalKobo,
            String ledgerHash,
            Instant createdAt,
            List<LineView> items
    ) {
    }

    public record LineView(UUID productId, String productName, int quantity, String unitPrice) {
    }
}
