package com.afrismart.twins;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.afrismart.shared.security.AuthUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/twins")
public class TwinController {

    private final TwinService twins;

    public TwinController(TwinService twins) {
        this.twins = twins;
    }

    @GetMapping("/stores/{storeId}")
    @PreAuthorize("hasRole('MERCHANT')")
    public TwinViews.ShopTwin shop(@PathVariable UUID storeId) {
        return twins.shopTwin(AuthUser.require(), storeId);
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('CONSUMER')")
    public TwinViews.CustomerTwin me() {
        return twins.customerTwin(AuthUser.require());
    }

    @PostMapping("/simulate")
    @PreAuthorize("hasRole('MERCHANT')")
    public TwinViews.Simulation simulate(@Valid @RequestBody TwinViews.SimulationRequest request) {
        return twins.simulate(AuthUser.require(), request);
    }
}
