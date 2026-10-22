package com.afrismart.inventory;

import java.util.UUID;

public record StockChangedEvent(UUID storeId, UUID productId, int quantity, long unitPriceKobo) {
}
