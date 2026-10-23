package com.afrismart.stores;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.afrismart.shared.api.ApiException;
import com.afrismart.shared.api.ErrorCodes;
import com.afrismart.shared.config.AfriSmartProperties;
import com.afrismart.shared.media.MediaService;
import com.afrismart.shared.security.AuthUser;
import com.afrismart.shared.security.Role;

@Service
public class StoreService {

    private final StoreRepository stores;
    private final AfriSmartProperties properties;
    private final MediaService media;

    public StoreService(StoreRepository stores, AfriSmartProperties properties, MediaService media) {
        this.stores = stores;
        this.properties = properties;
        this.media = media;
    }

    @Transactional
    public StoreResponse create(AuthUser merchant, UpsertStoreRequest request) {
        requireMerchant(merchant);
        Store store = new Store();
        store.setMerchantId(merchant.id());
        apply(store, request);
        stores.save(store);
        return toResponse(store);
    }

    @Transactional
    public StoreResponse update(AuthUser merchant, UUID storeId, UpsertStoreRequest request) {
        requireMerchant(merchant);
        Store store = stores.findByIdAndMerchantId(storeId, merchant.id())
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.STORE_NOT_FOUND, "Store not found"));
        apply(store, request);
        return toResponse(store);
    }

    @Transactional(readOnly = true)
    public StoreResponse get(UUID storeId) {
        return toResponse(require(storeId));
    }

    @Transactional(readOnly = true)
    public List<StoreResponse> listMine(AuthUser merchant) {
        requireMerchant(merchant);
        return stores.findByMerchantId(merchant.id()).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<StoreResponse> listActive() {
        return stores.findAll().stream()
                .filter(Store::isActive)
                .map(this::toResponse)
                .toList();
    }

    public Store require(UUID storeId) {
        return stores.findById(storeId)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.STORE_NOT_FOUND, "Store not found"));
    }

    public Store requireOwned(UUID storeId, UUID merchantId) {
        Store store = require(storeId);
        if (!store.getMerchantId().equals(merchantId)) {
            throw ApiException.forbidden(ErrorCodes.FORBIDDEN_STORE, "You do not own this store");
        }
        return store;
    }

    @Transactional(readOnly = true)
    public List<StoreResponse> findByName(String name) {
        return stores.findByNameContainingIgnoreCaseAndActiveTrue(name.trim()).stream()
                .map(this::toResponse)
                .toList();
    }

    public boolean isOpenNow(Store store) {
        return StoreHours.isOpen(store.getOpenAt(), store.getCloseAt(), LocalTime.now(properties.getClock().zoneId()));
    }

    private void apply(Store store, UpsertStoreRequest request) {
        store.setName(request.name().trim());
        store.setDescription(request.description() == null || request.description().isBlank()
                ? null : request.description().trim());
        store.setAddress(request.address().trim());
        store.setLatitude(request.latitude());
        store.setLongitude(request.longitude());
        store.setOpenAt(request.openAt());
        store.setCloseAt(request.closeAt());
        store.setDeliveryRadiusM(request.deliveryRadiusM() == null ? 5000 : request.deliveryRadiusM());
        store.setActive(true);
        if (request.imageMediaId() != null) {
            media.require(request.imageMediaId());
        }
        store.setImageMediaId(request.imageMediaId());
        store.setPhone(blankToNull(request.phone()));
        store.setCategory(blankToNull(request.category()));
    }

    private StoreResponse toResponse(Store store) {
        return StoreResponse.from(store, isOpenNow(store), media.urlFor(store.getImageMediaId()));
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void requireMerchant(AuthUser user) {
        if (user.role() != Role.MERCHANT) {
            throw ApiException.forbidden("FORBIDDEN", "Merchant role required");
        }
    }
}
