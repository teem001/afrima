package com.afrismart.payments;

import com.afrismart.shared.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private LedgerTransaction transaction;

    @Column(nullable = false, length = 128)
    private String account;

    @Column(name = "debit_kobo", nullable = false)
    private long debitKobo;

    @Column(name = "credit_kobo", nullable = false)
    private long creditKobo;

    public LedgerTransaction getTransaction() {
        return transaction;
    }

    public void setTransaction(LedgerTransaction transaction) {
        this.transaction = transaction;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public long getDebitKobo() {
        return debitKobo;
    }

    public void setDebitKobo(long debitKobo) {
        this.debitKobo = debitKobo;
    }

    public long getCreditKobo() {
        return creditKobo;
    }

    public void setCreditKobo(long creditKobo) {
        this.creditKobo = creditKobo;
    }
}
