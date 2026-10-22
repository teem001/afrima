package com.afrismart.orders;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, UUID> {

    List<CustomerOrder> findByConsumerIdOrderByCreatedAtDesc(UUID consumerId);

    List<CustomerOrder> findByStoreIdInOrderByCreatedAtDesc(List<UUID> storeIds);

    @Query("""
            select new com.afrismart.orders.SalesTally(o.storeId, l.productId, sum(l.quantity))
            from CustomerOrder o join o.lines l
            where o.storeId in :storeIds
              and o.createdAt >= :since
              and o.status <> :excluded
            group by o.storeId, l.productId
            """)
    List<SalesTally> unitsSoldSince(
            @Param("storeIds") Collection<UUID> storeIds,
            @Param("since") Instant since,
            @Param("excluded") OrderStatus excluded
    );
}
