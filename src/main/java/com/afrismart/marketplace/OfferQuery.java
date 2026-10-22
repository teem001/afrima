package com.afrismart.marketplace;

import java.util.Set;
import java.util.UUID;

public record OfferQuery(
        String text,
        UUID productId,
        double latitude,
        double longitude,
        int radiusMeters,
        int minQuantity,
        Set<UUID> excludeStoreIds,
        int limit
) {
}
