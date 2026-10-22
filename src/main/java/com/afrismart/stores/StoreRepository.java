package com.afrismart.stores;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRepository extends JpaRepository<Store, UUID> {

    List<Store> findByMerchantId(UUID merchantId);

    Optional<Store> findByIdAndMerchantId(UUID id, UUID merchantId);

    List<Store> findByNameContainingIgnoreCaseAndActiveTrue(String name);
}
