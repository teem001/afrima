package com.afrismart.stores;

import java.time.LocalTime;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpsertStoreRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 2000) String description,
        @NotBlank @Size(max = 512) String address,
        @DecimalMin("-90") @DecimalMax("90") double latitude,
        @DecimalMin("-180") @DecimalMax("180") double longitude,
        @NotNull LocalTime openAt,
        @NotNull LocalTime closeAt,
        @Min(100) Integer deliveryRadiusM,
        UUID imageMediaId,
        @Size(max = 32) String phone,
        @Size(max = 128) String category
) {
}
