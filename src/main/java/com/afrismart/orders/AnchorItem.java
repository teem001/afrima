package com.afrismart.orders;

import java.util.UUID;

import com.afrismart.shared.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "anchor_items")
public class AnchorItem extends BaseEntity {

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "ledger_hash", nullable = false, length = 64)
    private String ledgerHash;

    @Column(name = "leaf_index", nullable = false)
    private int leafIndex;

    public UUID getBatchId() { return batchId; }
    public void setBatchId(UUID batchId) { this.batchId = batchId; }
    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public String getLedgerHash() { return ledgerHash; }
    public void setLedgerHash(String ledgerHash) { this.ledgerHash = ledgerHash; }
    public int getLeafIndex() { return leafIndex; }
    public void setLeafIndex(int leafIndex) { this.leafIndex = leafIndex; }
}
