package com.afrismart.orders;

import java.util.UUID;

public record SalesTally(UUID storeId, UUID productId, Long units) {
}
