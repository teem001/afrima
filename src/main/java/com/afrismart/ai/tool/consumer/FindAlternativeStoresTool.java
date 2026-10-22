package com.afrismart.ai.tool.consumer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
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
import com.afrismart.ai.tool.ToolException;
import com.afrismart.ai.tool.ToolResult;
import com.afrismart.marketplace.NearbyOffer;
import com.afrismart.marketplace.OfferQuery;
import com.afrismart.marketplace.SearchService;
import com.afrismart.shared.geo.GeoUtils;
import com.afrismart.shared.security.Role;
import com.afrismart.stores.StoreResponse;
import com.afrismart.stores.StoreService;

@Component
public class FindAlternativeStoresTool implements AiTool {

    private final SearchService searchService;
    private final StoreService storeService;

    public FindAlternativeStoresTool(SearchService searchService, StoreService storeService) {
        this.searchService = searchService;
        this.storeService = storeService;
    }

    @Override
    public AiToolType type() {
        return AiToolType.FIND_ALTERNATIVE_STORES;
    }

    @Override
    public String description() {
        return "Reroute a purchase when the preferred store is out of stock or cannot cover the quantity. "
                + "Excludes the given store(s) and returns other nearby stores that can supply the full quantity, "
                + "with a recommended pick. Searches twice the normal radius.";
    }

    @Override
    public JsonSchema parameters() {
        return JsonSchema.object(Map.of(
                "query", JsonSchema.string("Product the user wants, if productId is not known"),
                "productId", JsonSchema.string("Exact product id from an earlier tool result"),
                "quantity", JsonSchema.integer("Units needed. Default 1."),
                "excludeStoreIds", JsonSchema.array("Store ids to skip", JsonSchema.string("Store id")),
                "excludeStoreName", JsonSchema.string("Name (or part of it) of the store to skip"),
                "radiusMeters", CommonParams.RADIUS,
                "latitude", CommonParams.LATITUDE,
                "longitude", CommonParams.LONGITUDE
        ));
    }

    @Override
    public Set<Role> roles() {
        return Set.of(Role.CONSUMER);
    }

    @Override
    public ToolResult execute(ToolArgs args, AiToolContext context) {
        String query = args.string("query");
        UUID productId = args.uuid("productId");
        if (query == null && productId == null) {
            throw ToolException.invalidArgument("Provide query or productId");
        }
        int quantity = Math.max(1, args.integer("quantity", 1));
        AiToolContext.GeoPoint here = context.requireLocation(args);
        int radius = args.integer("radiusMeters", searchService.defaultRadiusMeters() * 2);

        Set<UUID> excluded = new LinkedHashSet<>(args.uuids("excludeStoreIds"));
        List<String> excludedNames = new ArrayList<>();
        String excludeName = args.string("excludeStoreName");
        if (excludeName != null) {
            for (StoreResponse store : storeService.findByName(excludeName)) {
                excluded.add(store.id());
                excludedNames.add(store.name());
            }
        }

        List<NearbyOffer> offers = searchService.findOffers(new OfferQuery(
                query, productId, here.latitude(), here.longitude(), radius, quantity, excluded, 5
        ));
        if (offers.isEmpty()) {
            return ToolResult.ok(
                    "No other store within " + GeoUtils.roundKm(radius) + " km can supply " + quantity + ".",
                    new Alternatives(excludedNames, quantity, radius, null, List.of())
            );
        }
        return ToolResult.ok(
                offers.size() + " other store(s) can supply " + quantity + ". Recommended: " + offers.getFirst().storeName(),
                new Alternatives(excludedNames, quantity, radius, offers.getFirst(), offers)
        );
    }

    public record Alternatives(
            List<String> excludedStores,
            int quantity,
            int radiusMeters,
            NearbyOffer recommended,
            List<NearbyOffer> alternatives
    ) {
    }
}
