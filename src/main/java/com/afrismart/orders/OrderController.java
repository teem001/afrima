package com.afrismart.orders;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.afrismart.shared.security.AuthUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
public class OrderController {

    private final OrderService orderService;
    private final AnchorService anchorService;

    public OrderController(OrderService orderService, AnchorService anchorService) {
        this.orderService = orderService;
        this.anchorService = anchorService;
    }

    @PostMapping("/orders")
    @PreAuthorize("hasRole('CONSUMER')")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse place(@Valid @RequestBody PlaceOrderRequest request) {
        return orderService.place(AuthUser.require(), request);
    }

    @GetMapping("/orders")
    @PreAuthorize("hasRole('CONSUMER')")
    public List<OrderResponse> mine() {
        return orderService.listMine(AuthUser.require());
    }

    @GetMapping("/orders/{orderId}")
    public OrderResponse get(@PathVariable UUID orderId) {
        return orderService.get(AuthUser.require(), orderId);
    }

    @PostMapping("/orders/{orderId}/accept")
    @PreAuthorize("hasRole('MERCHANT')")
    public OrderResponse accept(@PathVariable UUID orderId) {
        return orderService.accept(AuthUser.require(), orderId);
    }

    @PostMapping("/orders/{orderId}/fulfil")
    @PreAuthorize("hasRole('MERCHANT')")
    public OrderResponse fulfil(@PathVariable UUID orderId) {
        return orderService.fulfil(AuthUser.require(), orderId);
    }

    @PostMapping("/orders/{orderId}/cancel")
    public OrderResponse cancel(@PathVariable UUID orderId) {
        return orderService.cancel(AuthUser.require(), orderId);
    }

    /** Public receipt verification: recompute the order hash and check its Merkle anchor. */
    @GetMapping("/verify/{orderId}")
    public AnchorService.VerifyView verify(@PathVariable UUID orderId) {
        return anchorService.verify(orderId);
    }

    @PostMapping("/anchors/pending")
    @PreAuthorize("hasRole('MERCHANT')")
    public AnchorService.AnchorView anchorPending() {
        return anchorService.anchorPending();
    }

    @GetMapping("/merchant/orders")
    @PreAuthorize("hasRole('MERCHANT')")
    public List<OrderResponse> merchantOrders() {
        return orderService.listForMerchant(AuthUser.require());
    }
}
