package com.afrismart.stores;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;

import org.junit.jupiter.api.Test;

class StoreHoursTest {

    private static final LocalTime EIGHT = LocalTime.of(8, 0);
    private static final LocalTime TWENTY = LocalTime.of(20, 0);

    @Test
    void daytimeHoursAreOpenBetweenOpenAndClose() {
        assertThat(StoreHours.isOpen(EIGHT, TWENTY, LocalTime.of(8, 0))).isTrue();
        assertThat(StoreHours.isOpen(EIGHT, TWENTY, LocalTime.of(19, 59))).isTrue();
        assertThat(StoreHours.isOpen(EIGHT, TWENTY, LocalTime.of(20, 0))).isFalse();
        assertThat(StoreHours.isOpen(EIGHT, TWENTY, LocalTime.of(7, 59))).isFalse();
    }

    @Test
    void overnightHoursStayOpenAfterMidnight() {
        LocalTime open = LocalTime.of(21, 0);
        LocalTime close = LocalTime.of(2, 0);
        assertThat(StoreHours.isOpen(open, close, LocalTime.of(22, 30))).isTrue();
        assertThat(StoreHours.isOpen(open, close, LocalTime.of(1, 59))).isTrue();
        assertThat(StoreHours.isOpen(open, close, LocalTime.of(2, 0))).isFalse();
        assertThat(StoreHours.isOpen(open, close, LocalTime.of(12, 0))).isFalse();
    }

    @Test
    void equalOpenAndCloseMeansAlwaysOpen() {
        assertThat(StoreHours.isOpen(LocalTime.MIDNIGHT, LocalTime.MIDNIGHT, LocalTime.of(3, 0))).isTrue();
    }
}
