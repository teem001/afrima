package com.afrismart.orders;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class LedgerHasherTest {

    @Test
    void sameCanonicalPayloadProducesSameHash() {
        UUID orderId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID consumer = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID store = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID pens = UUID.fromString("44444444-4444-4444-4444-444444444444");
        Instant created = Instant.parse("2026-09-22T12:00:00Z");
        List<LedgerHasher.Line> lines = List.of(new LedgerHasher.Line(pens, 2, 75_000));

        String first = LedgerHasher.hash(orderId, consumer, store, 150_000, "NGN", created, lines);
        String second = LedgerHasher.hash(orderId, consumer, store, 150_000, "NGN", created, lines);

        assertThat(first).hasSize(64).isEqualTo(second);
    }

    @Test
    void lineOrderDoesNotChangeHash() {
        UUID orderId = UUID.randomUUID();
        UUID consumer = UUID.randomUUID();
        UUID store = UUID.randomUUID();
        UUID pens = UUID.fromString("44444444-4444-4444-4444-444444444444");
        UUID rice = UUID.fromString("55555555-5555-5555-5555-555555555555");
        Instant created = Instant.parse("2026-09-22T12:00:00Z");

        String a = LedgerHasher.hash(orderId, consumer, store, 1, "NGN", created, List.of(
                new LedgerHasher.Line(rice, 1, 1),
                new LedgerHasher.Line(pens, 1, 1)
        ));
        String b = LedgerHasher.hash(orderId, consumer, store, 1, "NGN", created, List.of(
                new LedgerHasher.Line(pens, 1, 1),
                new LedgerHasher.Line(rice, 1, 1)
        ));

        assertThat(a).isEqualTo(b);
    }
}
