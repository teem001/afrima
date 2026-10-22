package com.afrismart.ai.tool.consumer;

import java.util.List;
import java.util.Map;
import java.util.Set;

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
import com.afrismart.shared.security.Role;

@Component
public class SearchProductsNearbyTool implements AiTool {

    private final SearchService searchService;

    public SearchProductsNearbyTool(SearchService searchService) {
        this.searchService = searchService;
    }

    @Override
    public AiToolType type() {
        return AiToolType.SEARCH_PRODUCTS_NEARBY;
    }

    @Override
    public String description() {
        return "Find nearby stores that have a product in stock, nearest first. Use the shopper's words as the query "
                + "(for example 'pack of pens', 'biro', 'rice'). Returns store, distance, price, and quantity available.";
    }

    @Override
    public JsonSchema parameters() {
        return JsonSchema.object(Map.of(
                "query", JsonSchema.string("What the user wants to buy"),
                "minQuantity", JsonSchema.integer("Only return stores holding at least this many units. Default 1."),
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
        AiToolContext.GeoPoint here = context.requireLocation(args);
        int radius = args.integer("radiusMeters", searchService.defaultRadiusMeters());
        int minQuantity = Math.max(1, args.integer("minQuantity", 1));
        List<NearbyOffer> offers = searchService.findOffers(new OfferQuery(
                query, null, here.latitude(), here.longitude(), radius, minQuantity, Set.of(), 10
        ));
        SearchResult result = new SearchResult(query, radius, offers.size(), offers);
        if (offers.isEmpty()) {
            return ToolResult.ok(
                    "No store within " + GeoUtils.roundKm(radius) + " km has '" + query + "' in stock.",
                    result
            );
        }
        return ToolResult.ok(result);
    }

    public record SearchResult(String query, int radiusMeters, int count, List<NearbyOffer> offers) {
    }
}
