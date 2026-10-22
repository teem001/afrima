package com.afrismart.marketplace;

import java.util.List;

public record SearchResponse(
        String query,
        double latitude,
        double longitude,
        int radiusMeters,
        int resultCount,
        Long minPriceKobo,
        Long maxPriceKobo,
        String minPrice,
        String maxPrice,
        List<NearbyOffer> offers
) {
}
