package com.afrismart.payments;

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
@Table(name = "ledger_transactions")
public class LedgerTransaction extends BaseEntity {

    @Column(nullable = false, length = 32)
    private String type;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(length = 512)
    private String description;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LedgerEntry> entries = new ArrayList<>();

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public UUID getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(UUID referenceId) {
        this.referenceId = referenceId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<LedgerEntry> getEntries() {
        return entries;
    }

    public void addEntry(LedgerEntry entry) {
        entry.setTransaction(this);
        entries.add(entry);
    }
}
