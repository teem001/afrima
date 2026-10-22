package com.afrismart.payments;

import java.util.UUID;

import com.afrismart.shared.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "wallets")
public class Wallet extends BaseEntity {

    @Column(name = "owner_id", nullable = false, unique = true)
    private UUID ownerId;

    @Column(name = "balance_kobo", nullable = false)
    private long balanceKobo;

    @Column(nullable = false, length = 8)
    private String currency = "NGN";

    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public long getBalanceKobo() {
        return balanceKobo;
    }

    public void setBalanceKobo(long balanceKobo) {
        this.balanceKobo = balanceKobo;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}
