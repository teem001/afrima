package com.afrismart.payments;

import java.util.UUID;

import com.afrismart.shared.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "escrow_holds")
public class EscrowHold extends BaseEntity {

    public enum Status { HELD, RELEASED, REFUNDED }

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "consumer_id", nullable = false)
    private UUID consumerId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "amount_kobo", nullable = false)
    private long amountKobo;

    @Column(name = "commission_kobo", nullable = false)
    private long commissionKobo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.HELD;

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public UUID getConsumerId() { return consumerId; }
    public void setConsumerId(UUID consumerId) { this.consumerId = consumerId; }
    public UUID getMerchantId() { return merchantId; }
    public void setMerchantId(UUID merchantId) { this.merchantId = merchantId; }
    public UUID getStoreId() { return storeId; }
    public void setStoreId(UUID storeId) { this.storeId = storeId; }
    public long getAmountKobo() { return amountKobo; }
    public void setAmountKobo(long amountKobo) { this.amountKobo = amountKobo; }
    public long getCommissionKobo() { return commissionKobo; }
    public void setCommissionKobo(long commissionKobo) { this.commissionKobo = commissionKobo; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
}
