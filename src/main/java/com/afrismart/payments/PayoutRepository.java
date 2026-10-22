package com.afrismart.payments;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PayoutRepository extends JpaRepository<Payout, UUID> {
    List<Payout> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
}
