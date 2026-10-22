package com.afrismart.stores;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.afrismart.shared.security.AuthUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/stores")
public class StoreController {

    private final StoreService storeService;

    public StoreController(StoreService storeService) {
        this.storeService = storeService;
    }

    @PostMapping
    @PreAuthorize("hasRole('MERCHANT')")
    @ResponseStatus(HttpStatus.CREATED)
    public StoreResponse create(@Valid @RequestBody UpsertStoreRequest request) {
        return storeService.create(AuthUser.require(), request);
    }

    @PutMapping("/{storeId}")
    @PreAuthorize("hasRole('MERCHANT')")
    public StoreResponse update(@PathVariable UUID storeId, @Valid @RequestBody UpsertStoreRequest request) {
        return storeService.update(AuthUser.require(), storeId, request);
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('MERCHANT')")
    public List<StoreResponse> mine() {
        return storeService.listMine(AuthUser.require());
    }

    @GetMapping
    public List<StoreResponse> list() {
        return storeService.listActive();
    }

    @GetMapping("/{storeId}")
    public StoreResponse get(@PathVariable UUID storeId) {
        return storeService.get(storeId);
    }
}
