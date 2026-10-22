package com.afrismart.catalog;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.afrismart.shared.api.ApiException;
import com.afrismart.shared.api.ErrorCodes;

@Service
public class CatalogService {

    private final ProductRepository products;

    public CatalogService(ProductRepository products) {
        this.products = products;
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        String sku = request.sku().trim().toUpperCase();
        if (products.existsBySkuIgnoreCase(sku)) {
            throw ApiException.conflict(ErrorCodes.SKU_TAKEN, "SKU already exists");
        }
        Product product = new Product();
        product.setSku(sku);
        product.setName(request.name().trim());
        product.setDescription(request.description());
        product.replaceAliases(request.aliases());
        products.save(product);
        return ProductResponse.from(product);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> list() {
        return products.findAll().stream().map(ProductResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse get(UUID productId) {
        return ProductResponse.from(require(productId));
    }

    @Transactional(readOnly = true)
    public Map<UUID, ProductResponse> getAll(Collection<UUID> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, ProductResponse> byId = new LinkedHashMap<>();
        products.findAllById(Set.copyOf(productIds))
                .forEach(product -> byId.put(product.getId(), ProductResponse.from(product)));
        return byId;
    }

    public Product require(UUID productId) {
        return products.findById(productId)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.PRODUCT_NOT_FOUND, "Product not found"));
    }
}
