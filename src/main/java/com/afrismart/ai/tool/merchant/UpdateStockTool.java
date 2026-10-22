package com.afrismart.ai.tool.merchant;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
import com.afrismart.ai.tool.ToolException;
import com.afrismart.ai.tool.ToolResult;
import com.afrismart.catalog.CatalogService;
import com.afrismart.inventory.InventoryItemResponse;
import com.afrismart.inventory.InventoryService;
import com.afrismart.inventory.UpsertInventoryRequest;
import com.afrismart.shared.security.Role;

@Component
public class UpdateStockTool implements AiTool {

    private final InventoryService inventoryService;
    private final CatalogService catalogService;

    public UpdateStockTool(InventoryService inventoryService, CatalogService catalogService) {
        this.inventoryService = inventoryService;
        this.catalogService = catalogService;
    }

    @Override
    public AiToolType type() {
        return AiToolType.UPDATE_STOCK;
    }

    @Override
    public String description() {
        return "Set the stock level (and optionally the price) of a product at one of the merchant's stores. "
                + "Quantity is the new total on the shelf, not an increment. Only call after the merchant has confirmed.";
    }

    @Override
    public JsonSchema parameters() {
        return JsonSchema.object(Map.of(
                "storeId", JsonSchema.string("Store id from an earlier tool result"),
                "productId", JsonSchema.string("Product id from an earlier tool result"),
                "quantity", JsonSchema.integer("New total units on hand, 0 or more"),
                "unitPriceNaira", JsonSchema.number("New unit price in naira. Omit to keep the current price.")
        ), "storeId", "productId", "quantity");
    }

    @Override
    public Set<Role> roles() {
        return Set.of(Role.MERCHANT);
    }

    @Override
    public ToolResult execute(ToolArgs args, AiToolContext context) {
        UUID storeId = args.requireUuid("storeId");
        UUID productId = args.requireUuid("productId");
        int quantity = args.integer("quantity", -1);
        if (quantity < 0) {
            throw ToolException.invalidArgument("quantity must be 0 or more");
        }
        Double priceNaira = args.decimal("unitPriceNaira");
        long priceKobo;
        if (priceNaira != null) {
            if (priceNaira <= 0) {
                throw ToolException.invalidArgument("unitPriceNaira must be greater than 0");
            }
            priceKobo = BigDecimal.valueOf(priceNaira).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
        } else {
            priceKobo = inventoryService.find(storeId, productId)
                    .map(InventoryItemResponse::unitPriceKobo)
                    .orElseThrow(() -> ToolException.invalidArgument(
                            "This product is new to the store, so unitPriceNaira is required"));
        }

        InventoryItemResponse updated = inventoryService.upsert(
                context.user(), storeId, new UpsertInventoryRequest(productId, quantity, priceKobo)
        );
        return ToolResult.ok("Stock updated", StockLine.of(updated, catalogService.get(productId)));
    }
}
