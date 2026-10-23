package com.afrismart.catalog;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.afrismart.shared.persistence.BaseEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "products")
public class Product extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "image_media_id")
    private UUID imageMediaId;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductAlias> aliases = new ArrayList<>();

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public UUID getImageMediaId() {
        return imageMediaId;
    }

    public void setImageMediaId(UUID imageMediaId) {
        this.imageMediaId = imageMediaId;
    }

    public List<ProductAlias> getAliases() {
        return aliases;
    }

    public void replaceAliases(List<String> values) {
        aliases.clear();
        if (values == null) {
            return;
        }
        values.stream()
                .map(String::trim)
                .filter(alias -> !alias.isBlank())
                .distinct()
                .forEach(alias -> {
                    ProductAlias entity = new ProductAlias();
                    entity.setProduct(this);
                    entity.setAlias(alias.toLowerCase());
                    aliases.add(entity);
                });
    }
}
