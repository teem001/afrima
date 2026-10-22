package com.afrismart.ai.tool.consumer;

import java.time.LocalTime;
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
import com.afrismart.ai.tool.StockLine;
import com.afrismart.ai.tool.ToolArgs;
import com.afrismart.ai.tool.ToolException;
import com.afrismart.ai.tool.ToolResult;
import com.afrismart.catalog.CatalogService;
import com.afrismart.catalog.ProductResponse;
import com.afrismart.inventory.InventoryItemResponse;
import com.afrismart.inventory.InventoryService;
import com.afrismart.shared.api.ErrorCodes;
import com.afrismart.shared.geo.GeoUtils;
import com.afrismart.shared.security.Role;
import com.afrismart.stores.StoreResponse;
import com.afrismart.stores.StoreService;

@Component
public class GetStoreDetailsTool implements AiTool {

    private final StoreService storeService;
    private final InventoryService inventoryService;
    private final CatalogService catalogService;

    public GetStoreDetailsTool(StoreService storeService, InventoryService inventoryService, CatalogService catalogService) {
        this.storeService = storeService;
        this.inventoryService = inventoryService;
        this.catalogService = catalogService;
    }

    @Override
    public AiToolType type() {
        return AiToolType.GET_STORE_DETAILS;
    }

    @Override
    public String description() {
        return "Look up one store by id or name: address, hours, whether it is open now, distance from the user, "
                + "and its stock with prices. Pass `query` to check one product, e.g. whether the user's preferred shop has pens.";
    }

    @Override
    public JsonSchema parameters() {
        return JsonSchema.object(Map.of(
                "storeId", JsonSchema.string("Store id from an earlier tool result"),
                "storeName", JsonSchema.string("Store name or part of it, if the id is not known"),
                "query", JsonSchema.string("Only show stock matching this product"),
                "latitude", CommonParams.LATITUDE,
                "longitude", CommonParams.LONGITUDE
        ));
    }

    @Override
    public Set<Role> roles() {
        return Set.of(Role.CONSUMER, Role.MERCHANT);
    }

    @Override
    public ToolResult execute(ToolArgs args, AiToolContext context) {
        UUID storeId = args.uuid("storeId");
        StoreResponse store;
        if (storeId != null) {
            store = storeService.get(storeId);
        } else {
            String name = args.string("storeName");
            if (name == null) {
                throw ToolException.invalidArgument("Provide storeId or storeName");
            }
            List<StoreResponse> matches = storeService.findByName(name);
            if (matches.isEmpty()) {
                return ToolResult.error(ErrorCodes.STORE_NOT_FOUND, "No store matches '" + name + "'");
            }
            if (matches.size() > 1) {
                return ToolResult.ok(
                        "Several stores match '" + name + "'. Ask the user which one.",
                        new Matches(matches.stream().map(s -> new Match(s.id(), s.name(), s.address())).toList())
                );
            }
            store = matches.getFirst();
        }

        List<InventoryItemResponse> items = inventoryService.listByStore(store.id());
        Map<UUID, ProductResponse> products = catalogService.getAll(
                items.stream().map(InventoryItemResponse::productId).toList()
        );
        String query = args.string("query");
        List<StockLine> stock = items.stream()
                .filter(item -> query == null || StockLine.matches(products.get(item.productId()), query))
                .map(item -> StockLine.of(item, products.get(item.productId())))
                .toList();
        Double distanceKm = context.location(args)
                .map(here -> GeoUtils.roundKm(GeoUtils.distanceMeters(
                        here.latitude(), here.longitude(), store.latitude(), store.longitude())))
                .orElse(null);

        StoreDetails details = new StoreDetails(
                store.id(), store.name(), store.address(), store.openAt(), store.closeAt(),
                store.openNow(), distanceKm, stock
        );
        if (query != null && stock.stream().noneMatch(StockLine::inStock)) {
            return ToolResult.ok(
                    store.name() + " has no stock of '" + query + "'. Use FIND_ALTERNATIVE_STORES to reroute.",
                    details
            );
        }
        return ToolResult.ok(details);
    }

    public record StoreDetails(
            UUID storeId,
            String name,
            String address,
            LocalTime openAt,
            LocalTime closeAt,
            boolean openNow,
            Double distanceKm,
            List<StockLine> stock
    ) {
    }

    public record Matches(List<Match> matches) {
    }

    public record Match(UUID storeId, String name, String address) {
    }
}
