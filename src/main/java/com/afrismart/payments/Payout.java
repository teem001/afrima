package com.afrismart.payments;

import java.util.UUID;

import com.afrismart.shared.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "payouts")
public class Payout extends BaseEntity {

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "amount_kobo", nullable = false)
    private long amountKobo;

    @Column(nullable = false, length = 24)
    private String status = "SIMULATED_PAID";

    @Column(name = "bank_label", nullable = false, length = 128)
    private String bankLabel = "Demo bank account";

    public UUID getMerchantId() { return merchantId; }
    public void setMerchantId(UUID merchantId) { this.merchantId = merchantId; }
    public long getAmountKobo() { return amountKobo; }
    public void setAmountKobo(long amountKobo) { this.amountKobo = amountKobo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getBankLabel() { return bankLabel; }
    public void setBankLabel(String bankLabel) { this.bankLabel = bankLabel; }
}
