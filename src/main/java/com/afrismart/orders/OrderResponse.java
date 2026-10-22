package com.afrismart.orders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.afrismart.shared.money.Money;

public record OrderResponse(
        UUID id,
        UUID consumerId,
        UUID storeId,
        OrderStatus status,
        long totalKobo,
        String total,
        String currency,
        String ledgerHash,
        Instant createdAt,
        List<Line> items
) {
    public record Line(UUID productId, int quantity, long unitPriceKobo, String unitPrice) {
    }

    public static OrderResponse from(CustomerOrder order) {
        List<Line> items = order.getLines().stream()
                .map(line -> new Line(
                        line.getProductId(),
                        line.getQuantity(),
                        line.getUnitPriceKobo(),
                        Money.formatNaira(line.getUnitPriceKobo())
                ))
                .toList();
        return new OrderResponse(
                order.getId(),
                order.getConsumerId(),
                order.getStoreId(),
                order.getStatus(),
                order.getTotalKobo(),
                Money.formatNaira(order.getTotalKobo()),
                order.getCurrency(),
                order.getLedgerHash(),
                order.getCreatedAt(),
                items
        );
    }
}
