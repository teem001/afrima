package com.afrismart.ai;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * What the client is showing when the user asks, so the assistant can answer "this", "here", or "my cart"
 * without another round trip. Supplied by the user's own device, so it is context, never authority.
 */
public record AiClientContext(
        @Size(max = 40) String screen,
        @Size(max = 200) String searchQuery,
        @Size(max = 120) String locationLabel,
        @Min(100) @Max(50_000) Integer radiusMeters,
        @Size(max = 255) String storeName,
        @Valid Cart cart
) {

    public record Cart(@Size(max = 255) String storeName, @Size(max = 20) List<@Valid CartLine> items) {
    }

    public record CartLine(@Size(max = 255) String productName, @Min(1) @Max(10_000) int quantity, @Size(max = 32) String unitPrice) {
    }
}
