package com.afrismart.orders;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AnchorBatchRepository extends JpaRepository<AnchorBatch, UUID> {
}
