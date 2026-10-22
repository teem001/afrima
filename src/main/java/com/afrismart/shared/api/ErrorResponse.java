package com.afrismart.shared.api;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        String code,
        String message,
        int status,
        Instant timestamp,
        List<String> details
) {
}
