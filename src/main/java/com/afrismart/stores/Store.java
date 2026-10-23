package com.afrismart.stores;

import java.time.LocalTime;
import java.util.UUID;

import com.afrismart.shared.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "stores")
public class Store extends BaseEntity {

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(name = "open_at", nullable = false)
    private LocalTime openAt;

    @Column(name = "close_at", nullable = false)
    private LocalTime closeAt;

    @Column(name = "delivery_radius_m", nullable = false)
    private int deliveryRadiusM = 5000;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "image_media_id")
    private UUID imageMediaId;

    @Column(length = 32)
    private String phone;

    @Column(length = 128)
    private String category;

    public UUID getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(UUID merchantId) {
        this.merchantId = merchantId;
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

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public LocalTime getOpenAt() {
        return openAt;
    }

    public void setOpenAt(LocalTime openAt) {
        this.openAt = openAt;
    }

    public LocalTime getCloseAt() {
        return closeAt;
    }

    public void setCloseAt(LocalTime closeAt) {
        this.closeAt = closeAt;
    }

    public int getDeliveryRadiusM() {
        return deliveryRadiusM;
    }

    public void setDeliveryRadiusM(int deliveryRadiusM) {
        this.deliveryRadiusM = deliveryRadiusM;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public UUID getImageMediaId() {
        return imageMediaId;
    }

    public void setImageMediaId(UUID imageMediaId) {
        this.imageMediaId = imageMediaId;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }
}
