package com.afrismart.orders;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AnchorItemRepository extends JpaRepository<AnchorItem, UUID> {
    Optional<AnchorItem> findByOrderId(UUID orderId);
    List<AnchorItem> findByBatchIdOrderByLeafIndexAsc(UUID batchId);
    boolean existsByOrderId(UUID orderId);
}
