package com.afrismart.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.afrismart.catalog.CatalogService;
import com.afrismart.shared.api.ApiException;
import com.afrismart.shared.api.ErrorCodes;
import com.afrismart.shared.money.Money;
import com.afrismart.shared.security.AuthUser;
import com.afrismart.stores.StoreService;

@Service
public class InventoryService {

    private final InventoryItemRepository items;
    private final StoreService storeService;
    private final CatalogService catalogService;
    private final ApplicationEventPublisher events;

    public InventoryService(
            InventoryItemRepository items,
            StoreService storeService,
            CatalogService catalogService,
            ApplicationEventPublisher events
    ) {
        this.items = items;
        this.storeService = storeService;
        this.catalogService = catalogService;
        this.events = events;
    }

    @Transactional
    public InventoryItemResponse upsert(AuthUser merchant, UUID storeId, UpsertInventoryRequest request) {
        storeService.requireOwned(storeId, merchant.id());
        catalogService.require(request.productId());
        InventoryItem item = items.findByStoreIdAndProductId(storeId, request.productId())
                .orElseGet(() -> {
                    InventoryItem created = new InventoryItem();
                    created.setStoreId(storeId);
                    created.setProductId(request.productId());
                    created.setCurrency(Money.NGN);
                    return created;
                });
        item.setQuantity(request.quantity());
        item.setUnitPriceKobo(request.unitPriceKobo());
        items.save(item);
        publishStockChanged(item);
        return InventoryItemResponse.from(item);
    }

    @Transactional(readOnly = true)
    public List<InventoryItemResponse> listByStore(UUID storeId) {
        storeService.require(storeId);
        return items.findByStoreId(storeId).stream().map(InventoryItemResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public Optional<InventoryItemResponse> find(UUID storeId, UUID productId) {
        return items.findByStoreIdAndProductId(storeId, productId).map(InventoryItemResponse::from);
    }

    @Transactional
    public List<ReservedStock> reserve(UUID storeId, List<OrderLineRequest> lines) {
        if (lines == null || lines.isEmpty()) {
            throw ApiException.badRequest(ErrorCodes.EMPTY_ORDER, "Order must contain at least one item");
        }
        List<ReservedStock> reserved = new ArrayList<>();
        for (OrderLineRequest line : lines) {
            InventoryItem item = items.lockByStoreIdAndProductId(storeId, line.productId())
                    .orElseThrow(() -> ApiException.conflict(
                            ErrorCodes.INSUFFICIENT_STOCK,
                            "Product is not stocked at this store"
                    ));
            if (item.getQuantity() < line.quantity()) {
                throw ApiException.conflict(
                        ErrorCodes.INSUFFICIENT_STOCK,
                        "Not enough stock for product " + line.productId()
                );
            }
            item.setQuantity(item.getQuantity() - line.quantity());
            reserved.add(new ReservedStock(item.getProductId(), line.quantity(), item.getUnitPriceKobo(), item.getCurrency()));
            publishStockChanged(item);
        }
        return reserved;
    }

    private void publishStockChanged(InventoryItem item) {
        events.publishEvent(new StockChangedEvent(
                item.getStoreId(), item.getProductId(), item.getQuantity(), item.getUnitPriceKobo()
        ));
    }

    public record OrderLineRequest(UUID productId, int quantity) {
    }
}
