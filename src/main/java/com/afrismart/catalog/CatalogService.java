package com.afrismart.catalog;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.afrismart.shared.api.ApiException;
import com.afrismart.shared.api.ErrorCodes;
import com.afrismart.shared.media.MediaService;

@Service
public class CatalogService {

    private final ProductRepository products;
    private final MediaService media;

    public CatalogService(ProductRepository products, MediaService media) {
        this.products = products;
        this.media = media;
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        String sku = request.sku() == null || request.sku().isBlank()
                ? generateSku(request.name())
                : request.sku().trim().toUpperCase(Locale.ROOT);
        if (products.existsBySkuIgnoreCase(sku)) {
            throw ApiException.conflict(ErrorCodes.SKU_TAKEN, "SKU already exists");
        }
        Product product = new Product();
        product.setSku(sku);
        product.setName(request.name().trim());
        product.setDescription(request.description() == null || request.description().isBlank()
                ? null : request.description().trim());
        product.replaceAliases(request.aliases());
        if (request.imageMediaId() != null) {
            media.require(request.imageMediaId());
            product.setImageMediaId(request.imageMediaId());
        }
        products.save(product);
        return toResponse(product);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> list() {
        return products.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse get(UUID productId) {
        return toResponse(require(productId));
    }

    @Transactional(readOnly = true)
    public Map<UUID, ProductResponse> getAll(Collection<UUID> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, ProductResponse> byId = new LinkedHashMap<>();
        products.findAllById(Set.copyOf(productIds))
                .forEach(product -> byId.put(product.getId(), toResponse(product)));
        return byId;
    }

    public Product require(UUID productId) {
        return products.findById(productId)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.PRODUCT_NOT_FOUND, "Product not found"));
    }

    private ProductResponse toResponse(Product product) {
        return ProductResponse.from(product, media.urlFor(product.getImageMediaId()));
    }

    private String generateSku(String name) {
        String slug = name.trim().toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (slug.length() > 24) {
            slug = slug.substring(0, 24);
        }
        if (slug.isBlank()) {
            slug = "ITEM";
        }
        for (int attempt = 0; attempt < 8; attempt++) {
            String candidate = slug + "-" + Integer.toHexString(ThreadLocalRandom.current().nextInt(0x100000, 0xFFFFF));
            if (!products.existsBySkuIgnoreCase(candidate)) {
                return candidate;
            }
        }
        return slug + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }
}
