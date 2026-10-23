package com.afrismart.inventory;

import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Adds units onto the shelf (does not replace the current quantity). */
public record RestockRequest(
        @NotNull UUID productId,
        @Min(1) int addQuantity,
        /** Optional; keep the existing shelf price when null. */
        @Min(0) Long unitPriceKobo
) {
}
