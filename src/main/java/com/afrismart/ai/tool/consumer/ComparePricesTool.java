package com.afrismart.ai.tool.consumer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.afrismart.ai.tool.AiTool;
import com.afrismart.ai.tool.AiToolContext;
import com.afrismart.ai.tool.AiToolType;
import com.afrismart.ai.tool.CommonParams;
import com.afrismart.ai.tool.JsonSchema;
import com.afrismart.ai.tool.ToolArgs;
import com.afrismart.ai.tool.ToolResult;
import com.afrismart.marketplace.NearbyOffer;
import com.afrismart.marketplace.OfferQuery;
import com.afrismart.marketplace.SearchService;
import com.afrismart.shared.geo.GeoUtils;
import com.afrismart.shared.money.Money;
import com.afrismart.shared.security.Role;

@Component
public class ComparePricesTool implements AiTool {

    private static final double PRICE_WEIGHT = 0.6;
    private static final double DISTANCE_WEIGHT = 0.4;

    private final SearchService searchService;

    public ComparePricesTool(SearchService searchService) {
        this.searchService = searchService;
    }

    @Override
    public AiToolType type() {
        return AiToolType.COMPARE_PRICES;
    }

    @Override
    public String description() {
        return "Compare prices for a product across nearby stores. Returns the price range and three picks per product: "
                + "cheapest, nearest, and best value (weighs price 60% and distance 40%).";
    }

    @Override
    public JsonSchema parameters() {
        return JsonSchema.object(Map.of(
                "query", JsonSchema.string("What the user wants to buy"),
                "quantity", JsonSchema.integer("How many units the user needs. Default 1."),
                "radiusMeters", CommonParams.RADIUS,
                "latitude", CommonParams.LATITUDE,
                "longitude", CommonParams.LONGITUDE
        ), "query");
    }

    @Override
    public Set<Role> roles() {
        return Set.of(Role.CONSUMER);
    }

    @Override
    public ToolResult execute(ToolArgs args, AiToolContext context) {
        String query = args.requireString("query");
        int quantity = Math.max(1, args.integer("quantity", 1));
        AiToolContext.GeoPoint here = context.requireLocation(args);
        int radius = args.integer("radiusMeters", searchService.defaultRadiusMeters());
        List<NearbyOffer> offers = searchService.findOffers(new OfferQuery(
                query, null, here.latitude(), here.longitude(), radius, quantity, Set.of(), 30
        ));
        if (offers.isEmpty()) {
            return ToolResult.ok(
                    "No store within " + GeoUtils.roundKm(radius) + " km has " + quantity + " of '" + query + "'.",
                    new Comparison(query, quantity, radius, List.of())
            );
        }

        Map<UUID, List<NearbyOffer>> byProduct = new LinkedHashMap<>();
        offers.forEach(offer -> byProduct.computeIfAbsent(offer.productId(), id -> new ArrayList<>()).add(offer));

        List<ProductComparison> products = byProduct.values().stream()
                .map(group -> compare(group, quantity))
                .toList();
        return ToolResult.ok(new Comparison(query, quantity, radius, products));
    }

    private ProductComparison compare(List<NearbyOffer> group, int quantity) {
        long minPrice = group.stream().mapToLong(NearbyOffer::unitPriceKobo).min().orElse(0);
        long maxPrice = group.stream().mapToLong(NearbyOffer::unitPriceKobo).max().orElse(0);
        double minDistance = group.stream().mapToDouble(NearbyOffer::distanceMeters).min().orElse(0);
        double maxDistance = group.stream().mapToDouble(NearbyOffer::distanceMeters).max().orElse(0);

        NearbyOffer cheapest = group.stream()
                .min(Comparator.comparingLong(NearbyOffer::unitPriceKobo).thenComparingDouble(NearbyOffer::distanceMeters))
                .orElseThrow();
        NearbyOffer nearest = group.stream()
                .min(Comparator.comparingDouble(NearbyOffer::distanceMeters))
                .orElseThrow();
        NearbyOffer bestValue = group.stream()
                .min(Comparator.comparingDouble(offer ->
                        PRICE_WEIGHT * normalize(offer.unitPriceKobo(), minPrice, maxPrice)
                                + DISTANCE_WEIGHT * normalize(offer.distanceMeters(), minDistance, maxDistance)))
                .orElseThrow();

        NearbyOffer first = group.getFirst();
        return new ProductComparison(
                first.productId(),
                first.sku(),
                first.productName(),
                group.size(),
                Money.formatNaira(minPrice),
                Money.formatNaira(maxPrice),
                pick(cheapest, quantity),
                pick(nearest, quantity),
                pick(bestValue, quantity)
        );
    }

    private static double normalize(double value, double min, double max) {
        return max == min ? 0 : (value - min) / (max - min);
    }

    private static Pick pick(NearbyOffer offer, int quantity) {
        return new Pick(
                offer.storeId(),
                offer.storeName(),
                offer.unitPrice(),
                Money.formatNaira(offer.unitPriceKobo() * quantity),
                GeoUtils.roundKm(offer.distanceMeters()),
                offer.quantity(),
                offer.openNow()
        );
    }

    public record Comparison(String query, int quantity, int radiusMeters, List<ProductComparison> products) {
    }

    public record ProductComparison(
            UUID productId,
            String sku,
            String productName,
            int storeCount,
            String minPrice,
            String maxPrice,
            Pick cheapest,
            Pick nearest,
            Pick bestValue
    ) {
    }

    public record Pick(
            UUID storeId,
            String storeName,
            String unitPrice,
            String totalForQuantity,
            double distanceKm,
            int quantityAvailable,
            boolean openNow
    ) {
    }
}
