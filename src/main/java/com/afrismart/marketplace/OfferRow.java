package com.afrismart.marketplace;

import java.time.LocalTime;
import java.util.UUID;

public record OfferRow(
        UUID storeId,
        String storeName,
        String address,
        Double latitude,
        Double longitude,
        LocalTime openAt,
        LocalTime closeAt,
        UUID storeImageMediaId,
        UUID productId,
        String sku,
        String productName,
        UUID productImageMediaId,
        Integer quantity,
        Long unitPriceKobo,
        String currency
) {
}
