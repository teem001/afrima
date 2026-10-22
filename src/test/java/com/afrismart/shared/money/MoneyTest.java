package com.afrismart.shared.money;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void convertsNairaToKobo() {
        assertThat(Money.nairaToKobo(new BigDecimal("750.00"))).isEqualTo(75_000);
        assertThat(Money.formatNaira(75_000)).isEqualTo("750.00");
    }
}
