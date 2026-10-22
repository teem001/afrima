package com.afrismart.ai.tool;

import java.util.Locale;
import java.util.UUID;

import com.afrismart.catalog.ProductResponse;
import com.afrismart.inventory.InventoryItemResponse;

public record StockLine(
        UUID productId,
        String sku,
        String productName,
        int quantity,
        boolean inStock,
        String unitPrice,
        long unitPriceKobo
) {

    public static StockLine of(InventoryItemResponse item, ProductResponse product) {
        return new StockLine(
                item.productId(),
                product == null ? null : product.sku(),
                product == null ? null : product.name(),
                item.quantity(),
                item.quantity() > 0,
                item.unitPrice(),
                item.unitPriceKobo()
        );
    }

    public static boolean matches(ProductResponse product, String query) {
        if (product == null || query == null) {
            return query == null;
        }
        String q = query.toLowerCase(Locale.ROOT).trim();
        return product.name().toLowerCase(Locale.ROOT).contains(q)
                || product.sku().toLowerCase(Locale.ROOT).contains(q)
                || product.aliases().stream().anyMatch(alias -> alias.contains(q) || q.contains(alias));
    }
}
