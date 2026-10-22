package com.afrismart.ai.tool.merchant;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.afrismart.ai.tool.AiTool;
import com.afrismart.ai.tool.AiToolContext;
import com.afrismart.ai.tool.AiToolType;
import com.afrismart.ai.tool.JsonSchema;
import com.afrismart.ai.tool.ToolArgs;
import com.afrismart.ai.tool.ToolResult;
import com.afrismart.catalog.CatalogService;
import com.afrismart.catalog.ProductResponse;
import com.afrismart.inventory.InventoryItemResponse;
import com.afrismart.inventory.InventoryService;
import com.afrismart.orders.OrderService;
import com.afrismart.orders.SalesTally;
import com.afrismart.shared.security.Role;
import com.afrismart.stores.StoreResponse;
import com.afrismart.stores.StoreService;

@Component
public class GetLowStockItemsTool implements AiTool {

    private static final Duration SALES_WINDOW = Duration.ofDays(7);

    private final StoreService storeService;
    private final InventoryService inventoryService;
    private final CatalogService catalogService;
    private final OrderService orderService;

    public GetLowStockItemsTool(
            StoreService storeService,
            InventoryService inventoryService,
            CatalogService catalogService,
            OrderService orderService
    ) {
        this.storeService = storeService;
        this.inventoryService = inventoryService;
        this.catalogService = catalogService;
        this.orderService = orderService;
    }

    @Override
    public AiToolType type() {
        return AiToolType.GET_LOW_STOCK_ITEMS;
    }

    @Override
    public String description() {
        return "Find products at or below a stock threshold across the merchant's stores, with units sold in the "
                + "last 7 days and a suggested restock quantity (enough for about two weeks of recent demand).";
    }

    @Override
    public JsonSchema parameters() {
        return JsonSchema.object(Map.of(
                "threshold", JsonSchema.integer("Flag items with this many units or fewer. Default 10.")
        ));
    }

    @Override
    public Set<Role> roles() {
        return Set.of(Role.MERCHANT);
    }

    @Override
    public ToolResult execute(ToolArgs args, AiToolContext context) {
        int threshold = Math.max(0, args.integer("threshold", 10));
        List<StoreResponse> stores = storeService.listMine(context.user());
        Map<UUID, String> storeNames = stores.stream()
                .collect(Collectors.toMap(StoreResponse::id, StoreResponse::name));
        Map<String, Long> sold = orderService.unitsSoldSince(storeNames.keySet(), Instant.now().minus(SALES_WINDOW))
                .stream()
                .collect(Collectors.toMap(tally -> key(tally.storeId(), tally.productId()), SalesTally::units));

        List<InventoryItemResponse> low = new ArrayList<>();
        for (StoreResponse store : stores) {
            inventoryService.listByStore(store.id()).stream()
                    .filter(item -> item.quantity() <= threshold)
                    .forEach(low::add);
        }
        Map<UUID, ProductResponse> products = catalogService.getAll(
                low.stream().map(InventoryItemResponse::productId).toList()
        );

        List<LowStockItem> items = low.stream()
                .map(item -> {
                    long unitsSold = sold.getOrDefault(key(item.storeId(), item.productId()), 0L);
                    long target = Math.max(threshold * 2L, unitsSold * 2);
                    ProductResponse product = products.get(item.productId());
                    return new LowStockItem(
                            item.storeId(),
                            storeNames.get(item.storeId()),
                            item.productId(),
                            product == null ? null : product.name(),
                            item.quantity(),
                            unitsSold,
                            Math.max(0, target - item.quantity())
                    );
                })
                .sorted(Comparator.comparingInt(LowStockItem::quantity)
                        .thenComparing(Comparator.comparingLong(LowStockItem::unitsSoldLast7Days).reversed()))
                .toList();

        LowStock result = new LowStock(threshold, items);
        if (items.isEmpty()) {
            return ToolResult.ok("Nothing is at or below " + threshold + " units.", result);
        }
        return ToolResult.ok(result);
    }

    private static String key(UUID storeId, UUID productId) {
        return storeId + ":" + productId;
    }

    public record LowStock(int threshold, List<LowStockItem> items) {
    }

    public record LowStockItem(
            UUID storeId,
            String storeName,
            UUID productId,
            String productName,
            int quantity,
            long unitsSoldLast7Days,
            long suggestedRestock
    ) {
    }
}
