package com.afrismart.stores;

import java.time.LocalTime;
import java.util.UUID;

public record StoreResponse(
        UUID id,
        UUID merchantId,
        String name,
        String description,
        String address,
        double latitude,
        double longitude,
        LocalTime openAt,
        LocalTime closeAt,
        int deliveryRadiusM,
        boolean active,
        boolean openNow,
        UUID imageMediaId,
        String imageUrl,
        String phone,
        String category
) {

    public static StoreResponse from(Store store, boolean openNow, String imageUrl) {
        return new StoreResponse(
                store.getId(),
                store.getMerchantId(),
                store.getName(),
                store.getDescription(),
                store.getAddress(),
                store.getLatitude(),
                store.getLongitude(),
                store.getOpenAt(),
                store.getCloseAt(),
                store.getDeliveryRadiusM(),
                store.isActive(),
                openNow,
                store.getImageMediaId(),
                imageUrl,
                store.getPhone(),
                store.getCategory()
        );
    }
}
