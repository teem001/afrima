package com.afrismart.orders;

import com.afrismart.shared.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "anchor_batches")
public class AnchorBatch extends BaseEntity {

    @Column(name = "merkle_root", nullable = false, length = 64)
    private String merkleRoot;

    @Column(name = "tx_reference", nullable = false, length = 128)
    private String txReference;

    @Column(name = "order_count", nullable = false)
    private int orderCount;

    /** SIMULATED until a real public-chain client replaces the anchor writer. */
    @Column(nullable = false, length = 16)
    private String status = "SIMULATED";

    public String getMerkleRoot() { return merkleRoot; }
    public void setMerkleRoot(String merkleRoot) { this.merkleRoot = merkleRoot; }
    public String getTxReference() { return txReference; }
    public void setTxReference(String txReference) { this.txReference = txReference; }
    public int getOrderCount() { return orderCount; }
    public void setOrderCount(int orderCount) { this.orderCount = orderCount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
