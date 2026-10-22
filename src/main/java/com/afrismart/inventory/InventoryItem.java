package com.afrismart.inventory;

import java.util.UUID;

import com.afrismart.shared.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "inventory_items", uniqueConstraints = @UniqueConstraint(columnNames = {"store_id", "product_id"}))
public class InventoryItem extends BaseEntity {

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price_kobo", nullable = false)
    private long unitPriceKobo;

    @Column(nullable = false)
    private String currency = "NGN";

    public UUID getStoreId() {
        return storeId;
    }

    public void setStoreId(UUID storeId) {
        this.storeId = storeId;
    }

    public UUID getProductId() {
        return productId;
    }

    public void setProductId(UUID productId) {
        this.productId = productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public long getUnitPriceKobo() {
        return unitPriceKobo;
    }

    public void setUnitPriceKobo(long unitPriceKobo) {
        this.unitPriceKobo = unitPriceKobo;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}
