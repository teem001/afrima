package com.afrismart.marketplace;

import java.util.UUID;

public record NearbyOffer(
        UUID storeId,
        String storeName,
        String address,
        double latitude,
        double longitude,
        boolean openNow,
        UUID productId,
        String sku,
        String productName,
        int quantity,
        long unitPriceKobo,
        String unitPrice,
        String currency,
        double distanceMeters
) {
}
