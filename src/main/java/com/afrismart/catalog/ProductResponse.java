package com.afrismart.catalog;

import java.util.List;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String sku,
        String name,
        String description,
        List<String> aliases,
        UUID imageMediaId,
        String imageUrl
) {

    public static ProductResponse from(Product product, String imageUrl) {
        List<String> aliases = product.getAliases().stream().map(ProductAlias::getAlias).toList();
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                aliases,
                product.getImageMediaId(),
                imageUrl
        );
    }
}
