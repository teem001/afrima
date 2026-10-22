package com.afrismart.inventory;

import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpsertInventoryRequest(
        @NotNull UUID productId,
        @Min(0) int quantity,
        @Min(0) long unitPriceKobo
) {
}
