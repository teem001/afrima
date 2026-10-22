package com.afrismart.marketplace;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.afrismart.shared.geo.GeoUtils.BoundingBox;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

@Repository
public class OfferRepository {

    private static final String BASE = """
            select new com.afrismart.marketplace.OfferRow(
                s.id, s.name, s.address, s.latitude, s.longitude, s.openAt, s.closeAt,
                p.id, p.sku, p.name, i.quantity, i.unitPriceKobo, i.currency)
            from Store s, InventoryItem i, Product p
            where i.storeId = s.id
              and p.id = i.productId
              and s.active = true
              and i.quantity >= :minQuantity
              and s.latitude between :minLat and :maxLat
              and s.longitude between :minLng and :maxLng
            """;

    private static final String TEXT_MATCH = """
             and (lower(p.name) like :pattern
                  or lower(p.sku) like :pattern
                  or exists (select a.id from ProductAlias a where a.product = p and a.alias like :pattern))
            """;

    @PersistenceContext
    private EntityManager entityManager;

    public List<OfferRow> findCandidates(OfferQuery query, BoundingBox box) {
        StringBuilder jpql = new StringBuilder(BASE);
        jpql.append(query.productId() != null ? " and p.id = :productId" : TEXT_MATCH);
        if (!query.excludeStoreIds().isEmpty()) {
            jpql.append(" and s.id not in :excluded");
        }

        TypedQuery<OfferRow> typed = entityManager.createQuery(jpql.toString(), OfferRow.class)
                .setParameter("minQuantity", query.minQuantity())
                .setParameter("minLat", box.minLat())
                .setParameter("maxLat", box.maxLat())
                .setParameter("minLng", box.minLng())
                .setParameter("maxLng", box.maxLng());
        if (query.productId() != null) {
            typed.setParameter("productId", query.productId());
        } else {
            typed.setParameter("pattern", "%" + query.text().trim().toLowerCase() + "%");
        }
        if (!query.excludeStoreIds().isEmpty()) {
            typed.setParameter("excluded", query.excludeStoreIds());
        }
        return typed.getResultList();
    }
}
