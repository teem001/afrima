package com.afrismart.marketplace;

import java.util.UUID;

public record NearbyOffer(
        UUID storeId,
        String storeName,
        String address,
        double latitude,
        double longitude,
        boolean openNow,
        String storeImageUrl,
        UUID productId,
        String sku,
        String productName,
        String productImageUrl,
        int quantity,
        long unitPriceKobo,
        String unitPrice,
        String currency,
        double distanceMeters
) {
}
