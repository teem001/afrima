package com.afrismart.inventory;

import java.util.UUID;

public record ReservedStock(UUID productId, int quantity, long unitPriceKobo, String currency) {
}
