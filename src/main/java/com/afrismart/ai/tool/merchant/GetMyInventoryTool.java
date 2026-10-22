package com.afrismart.ai.tool.merchant;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.afrismart.ai.tool.AiTool;
import com.afrismart.ai.tool.AiToolContext;
import com.afrismart.ai.tool.AiToolType;
import com.afrismart.ai.tool.JsonSchema;
import com.afrismart.ai.tool.StockLine;
import com.afrismart.ai.tool.ToolArgs;
import com.afrismart.ai.tool.ToolResult;
import com.afrismart.catalog.CatalogService;
import com.afrismart.catalog.ProductResponse;
import com.afrismart.inventory.InventoryItemResponse;
import com.afrismart.inventory.InventoryService;
import com.afrismart.shared.security.Role;
import com.afrismart.stores.StoreResponse;
import com.afrismart.stores.StoreService;

@Component
public class GetMyInventoryTool implements AiTool {

    private final StoreService storeService;
    private final InventoryService inventoryService;
    private final CatalogService catalogService;

    public GetMyInventoryTool(StoreService storeService, InventoryService inventoryService, CatalogService catalogService) {
        this.storeService = storeService;
        this.inventoryService = inventoryService;
        this.catalogService = catalogService;
    }

    @Override
    public AiToolType type() {
        return AiToolType.GET_MY_INVENTORY;
    }

    @Override
    public String description() {
        return "List stock and prices for every store the merchant owns. Pass `query` to look at one product.";
    }

    @Override
    public JsonSchema parameters() {
        return JsonSchema.object(Map.of(
                "storeId", JsonSchema.string("Only this store. Omit for all of the merchant's stores."),
                "query", JsonSchema.string("Only show stock matching this product")
        ));
    }

    @Override
    public Set<Role> roles() {
        return Set.of(Role.MERCHANT);
    }

    @Override
    public ToolResult execute(ToolArgs args, AiToolContext context) {
        UUID storeId = args.uuid("storeId");
        String query = args.string("query");
        List<StoreResponse> stores = storeService.listMine(context.user()).stream()
                .filter(store -> storeId == null || store.id().equals(storeId))
                .toList();
        if (stores.isEmpty()) {
            return ToolResult.ok("The merchant has no matching stores.", new Inventory(List.of()));
        }

        List<StoreStock> result = stores.stream().map(store -> {
            List<InventoryItemResponse> items = inventoryService.listByStore(store.id());
            Map<UUID, ProductResponse> products = catalogService.getAll(
                    items.stream().map(InventoryItemResponse::productId).toList()
            );
            List<StockLine> stock = items.stream()
                    .filter(item -> query == null || StockLine.matches(products.get(item.productId()), query))
                    .map(item -> StockLine.of(item, products.get(item.productId())))
                    .toList();
            return new StoreStock(store.id(), store.name(), stock);
        }).toList();
        return ToolResult.ok(new Inventory(result));
    }

    public record Inventory(List<StoreStock> stores) {
    }

    public record StoreStock(UUID storeId, String storeName, List<StockLine> stock) {
    }
}
