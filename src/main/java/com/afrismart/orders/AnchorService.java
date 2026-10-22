package com.afrismart.orders;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.afrismart.shared.api.ApiException;
import com.afrismart.shared.api.ErrorCodes;

/**
 * Anchors order ledger hashes in Merkle batches. Demo scope: the batch is recorded as SIMULATED with a
 * locally generated reference; swapping in a public-chain client only changes how txReference is produced.
 */
@Service
public class AnchorService {

    private final CustomerOrderRepository orders;
    private final AnchorBatchRepository batches;
    private final AnchorItemRepository items;

    public AnchorService(CustomerOrderRepository orders, AnchorBatchRepository batches, AnchorItemRepository items) {
        this.orders = orders;
        this.batches = batches;
        this.items = items;
    }

    public record AnchorView(UUID batchId, String merkleRoot, String txReference, String status, int orderCount) {
    }

    public record VerifyView(
            UUID orderId, String ledgerHash, boolean hashMatches, boolean anchored,
            String merkleRoot, String txReference, String anchorStatus, boolean proofValid
    ) {
    }

    @Transactional
    public AnchorView anchorOrder(CustomerOrder order) {
        return items.findByOrderId(order.getId())
                .map(item -> view(batches.getReferenceById(item.getBatchId())))
                .orElseGet(() -> createBatch(List.of(order)));
    }

    @Transactional
    public AnchorView anchorPending() {
        List<CustomerOrder> pending = orders.findAll().stream()
                .filter(order -> !items.existsByOrderId(order.getId()))
                .sorted(Comparator.comparing(CustomerOrder::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(100)
                .toList();
        if (pending.isEmpty()) {
            return null;
        }
        return createBatch(pending);
    }

    private AnchorView createBatch(List<CustomerOrder> batchOrders) {
        List<String> leaves = batchOrders.stream().map(CustomerOrder::getLedgerHash).toList();
        AnchorBatch batch = new AnchorBatch();
        batch.setMerkleRoot(MerkleTree.root(leaves));
        batch.setTxReference("SIM-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24).toUpperCase());
        batch.setOrderCount(batchOrders.size());
        batches.save(batch);
        for (int i = 0; i < batchOrders.size(); i++) {
            AnchorItem item = new AnchorItem();
            item.setBatchId(batch.getId());
            item.setOrderId(batchOrders.get(i).getId());
            item.setLedgerHash(batchOrders.get(i).getLedgerHash());
            item.setLeafIndex(i);
            items.save(item);
        }
        return view(batch);
    }

    @Transactional(readOnly = true)
    public VerifyView verify(UUID orderId) {
        CustomerOrder order = orders.findById(orderId)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.ORDER_NOT_FOUND, "Order not found"));
        String recomputed = LedgerHasher.hash(
                order.getId(), order.getConsumerId(), order.getStoreId(), order.getTotalKobo(),
                order.getCurrency(), order.getCreatedAt(),
                order.getLines().stream()
                        .map(line -> new LedgerHasher.Line(line.getProductId(), line.getQuantity(), line.getUnitPriceKobo()))
                        .toList());
        boolean matches = recomputed.equals(order.getLedgerHash());
        return items.findByOrderId(orderId).map(item -> {
            AnchorBatch batch = batches.getReferenceById(item.getBatchId());
            List<String> leaves = items.findByBatchIdOrderByLeafIndexAsc(batch.getId()).stream()
                    .map(AnchorItem::getLedgerHash)
                    .toList();
            String derivedRoot = MerkleTree.verify(order.getLedgerHash(), MerkleTree.proof(leaves, item.getLeafIndex()));
            return new VerifyView(orderId, order.getLedgerHash(), matches, true,
                    batch.getMerkleRoot(), batch.getTxReference(), batch.getStatus(),
                    derivedRoot.equals(batch.getMerkleRoot()));
        }).orElseGet(() -> new VerifyView(orderId, order.getLedgerHash(), matches, false, null, null, null, false));
    }

    private static AnchorView view(AnchorBatch batch) {
        return new AnchorView(batch.getId(), batch.getMerkleRoot(), batch.getTxReference(), batch.getStatus(), batch.getOrderCount());
    }
}
