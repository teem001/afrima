package com.afrismart.payments;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EscrowHoldRepository extends JpaRepository<EscrowHold, UUID> {
    Optional<EscrowHold> findByOrderId(UUID orderId);
    List<EscrowHold> findByMerchantIdAndStatus(UUID merchantId, EscrowHold.Status status);
    List<EscrowHold> findByConsumerIdAndStatus(UUID consumerId, EscrowHold.Status status);
    List<EscrowHold> findByMerchantId(UUID merchantId);
}
