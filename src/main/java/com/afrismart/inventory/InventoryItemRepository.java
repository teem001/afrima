package com.afrismart.inventory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {

    List<InventoryItem> findByStoreId(UUID storeId);

    Optional<InventoryItem> findByStoreIdAndProductId(UUID storeId, UUID productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryItem i where i.storeId = :storeId and i.productId = :productId")
    Optional<InventoryItem> lockByStoreIdAndProductId(@Param("storeId") UUID storeId, @Param("productId") UUID productId);
}
