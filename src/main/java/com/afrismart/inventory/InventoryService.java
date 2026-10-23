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

    /** Adds units to whatever is already on the shelf (for low / out-of-stock restocks). */
    @Transactional
    public InventoryItemResponse restock(AuthUser merchant, UUID storeId, RestockRequest request) {
        storeService.requireOwned(storeId, merchant.id());
        catalogService.require(request.productId());
        InventoryItem item = items.findByStoreIdAndProductId(storeId, request.productId())
                .orElseGet(() -> {
                    InventoryItem created = new InventoryItem();
                    created.setStoreId(storeId);
                    created.setProductId(request.productId());
                    created.setCurrency(Money.NGN);
                    created.setQuantity(0);
                    created.setUnitPriceKobo(0);
                    return created;
                });
        long nextQty = (long) item.getQuantity() + request.addQuantity();
        if (nextQty > Integer.MAX_VALUE) {
            throw ApiException.badRequest(ErrorCodes.VALIDATION_FAILED, "Restock quantity is too large");
        }
        item.setQuantity((int) nextQty);
        if (request.unitPriceKobo() != null) {
            item.setUnitPriceKobo(request.unitPriceKobo());
        } else if (item.getUnitPriceKobo() <= 0) {
            throw ApiException.badRequest(ErrorCodes.VALIDATION_FAILED, "Set a unit price before restocking a new product");
        }
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

    /** Puts reserved stock back on the shelf (order cancelled before fulfilment). */
    @Transactional
    public void restore(UUID storeId, List<OrderLineRequest> lines) {
        for (OrderLineRequest line : lines) {
            InventoryItem item = items.lockByStoreIdAndProductId(storeId, line.productId()).orElse(null);
            if (item == null) {
                continue;
            }
            item.setQuantity(item.getQuantity() + line.quantity());
            publishStockChanged(item);
        }
    }

    private void publishStockChanged(InventoryItem item) {
        events.publishEvent(new StockChangedEvent(
                item.getStoreId(), item.getProductId(), item.getQuantity(), item.getUnitPriceKobo()
        ));
    }

    public record OrderLineRequest(UUID productId, int quantity) {
    }
}
