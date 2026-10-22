package com.afrismart.payments;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, UUID> {

    @Query("""
            select distinct t from LedgerTransaction t join t.entries e
            where e.account = :account order by t.createdAt desc
            """)
    List<LedgerTransaction> findByAccount(@Param("account") String account);
}
