package com.afrismart.marketplace;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.afrismart.shared.api.ApiException;
import com.afrismart.shared.api.ErrorCodes;
import com.afrismart.shared.config.AfriSmartProperties;
import com.afrismart.shared.geo.GeoUtils;
import com.afrismart.shared.media.MediaService;
import com.afrismart.shared.money.Money;
import com.afrismart.stores.StoreHours;

@Service
public class SearchService {

    private static final int MIN_RADIUS_METERS = 100;
    private static final int MAX_RADIUS_METERS = 50_000;

    private final OfferRepository offers;
    private final AfriSmartProperties properties;
    private final MediaService media;

    public SearchService(OfferRepository offers, AfriSmartProperties properties, MediaService media) {
        this.offers = offers;
        this.properties = properties;
        this.media = media;
    }

    @Transactional(readOnly = true)
    public SearchResponse search(String query, double latitude, double longitude, Integer radiusMeters) {
        String normalized = query == null ? "" : query.trim();
        int radius = clampRadius(radiusMeters == null ? defaultRadiusMeters() : radiusMeters);
        List<NearbyOffer> found = findOffers(new OfferQuery(
                normalized, null, latitude, longitude, radius, 1, Set.of(), properties.getSearch().getMaxResults()
        ));
        Long min = found.stream().map(NearbyOffer::unitPriceKobo).min(Long::compareTo).orElse(null);
        Long max = found.stream().map(NearbyOffer::unitPriceKobo).max(Long::compareTo).orElse(null);
        return new SearchResponse(
                normalized,
                latitude,
                longitude,
                radius,
                found.size(),
                min,
                max,
                min == null ? null : Money.formatNaira(min),
                max == null ? null : Money.formatNaira(max),
                found
        );
    }

    @Transactional(readOnly = true)
    public List<NearbyOffer> findOffers(OfferQuery query) {
        if (query.productId() == null && (query.text() == null || query.text().trim().length() < 2)) {
            throw ApiException.badRequest(ErrorCodes.VALIDATION_FAILED, "Query must be at least 2 characters");
        }
        int radius = clampRadius(query.radiusMeters());
        LocalTime now = LocalTime.now(properties.getClock().zoneId());
        return offers.findCandidates(query, GeoUtils.boundingBox(query.latitude(), query.longitude(), radius)).stream()
                .map(row -> toOffer(row, query, now))
                .filter(offer -> offer.distanceMeters() <= radius)
                .sorted(Comparator.comparingDouble(NearbyOffer::distanceMeters)
                        .thenComparingLong(NearbyOffer::unitPriceKobo))
                .limit(Math.max(1, query.limit()))
                .toList();
    }

    public int defaultRadiusMeters() {
        return properties.getSearch().getDefaultRadiusMeters();
    }

    private int clampRadius(int radiusMeters) {
        return Math.max(MIN_RADIUS_METERS, Math.min(MAX_RADIUS_METERS, radiusMeters));
    }

    private NearbyOffer toOffer(OfferRow row, OfferQuery query, LocalTime now) {
        double distance = GeoUtils.distanceMeters(query.latitude(), query.longitude(), row.latitude(), row.longitude());
        return new NearbyOffer(
                row.storeId(),
                row.storeName(),
                row.address(),
                row.latitude(),
                row.longitude(),
                StoreHours.isOpen(row.openAt(), row.closeAt(), now),
                media.urlFor(row.storeImageMediaId()),
                row.productId(),
                row.sku(),
                row.productName(),
                media.urlFor(row.productImageMediaId()),
                row.quantity(),
                row.unitPriceKobo(),
                Money.formatNaira(row.unitPriceKobo()),
                row.currency(),
                Math.round(distance)
        );
    }
}
