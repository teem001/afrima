package com.afrismart.catalog;

import java.util.List;
import java.util.UUID;

public record ProductResponse(UUID id, String sku, String name, String description, List<String> aliases) {

    public static ProductResponse from(Product product) {
        List<String> aliases = product.getAliases().stream().map(ProductAlias::getAlias).toList();
        return new ProductResponse(product.getId(), product.getSku(), product.getName(), product.getDescription(), aliases);
    }
}
