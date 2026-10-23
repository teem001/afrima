package com.afrismart.inventory;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.afrismart.shared.security.AuthUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PutMapping
    @PreAuthorize("hasRole('MERCHANT')")
    public InventoryItemResponse upsert(
            @PathVariable UUID storeId,
            @Valid @RequestBody UpsertInventoryRequest request
    ) {
        return inventoryService.upsert(AuthUser.require(), storeId, request);
    }

    @PostMapping("/restock")
    @PreAuthorize("hasRole('MERCHANT')")
    public InventoryItemResponse restock(
            @PathVariable UUID storeId,
            @Valid @RequestBody RestockRequest request
    ) {
        return inventoryService.restock(AuthUser.require(), storeId, request);
    }

    @GetMapping
    public List<InventoryItemResponse> list(@PathVariable UUID storeId) {
        return inventoryService.listByStore(storeId);
    }
}
