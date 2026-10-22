package com.afrismart.inventory;

import java.util.UUID;

import com.afrismart.shared.money.Money;

public record InventoryItemResponse(
        UUID id,
        UUID storeId,
        UUID productId,
        int quantity,
        long unitPriceKobo,
        String unitPrice,
        String currency
) {

    public static InventoryItemResponse from(InventoryItem item) {
        return new InventoryItemResponse(
                item.getId(),
                item.getStoreId(),
                item.getProductId(),
                item.getQuantity(),
                item.getUnitPriceKobo(),
                Money.formatNaira(item.getUnitPriceKobo()),
                item.getCurrency()
        );
    }
}
