package com.afrismart.orders;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public final class LedgerHasher {

    private LedgerHasher() {
    }

    public static String hash(
            UUID orderId,
            UUID consumerId,
            UUID storeId,
            long totalKobo,
            String currency,
            Instant createdAt,
            List<Line> lines
    ) {
        String canonical = lines.stream()
                .sorted(Comparator.comparing(Line::productId))
                .map(line -> line.productId() + ":" + line.quantity() + ":" + line.unitPriceKobo())
                .collect(Collectors.joining("|"));
        String payload = String.join("|",
                orderId.toString(),
                consumerId.toString(),
                storeId.toString(),
                Long.toString(totalKobo),
                currency,
                createdAt.toString(),
                canonical
        );
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    public record Line(UUID productId, int quantity, long unitPriceKobo) {
    }
}
