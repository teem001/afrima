package com.afrismart.orders;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record PlaceOrderRequest(
        @NotNull UUID storeId,
        @NotEmpty List<@Valid Item> items
) {
    public record Item(@NotNull UUID productId, @Min(1) int quantity) {
    }
}
