package com.afrismart.shared.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {

    public static final String NGN = "NGN";

    private Money() {
    }

    public static long nairaToKobo(BigDecimal naira) {
        return naira.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    public static BigDecimal koboToNaira(long kobo) {
        return BigDecimal.valueOf(kobo, 2);
    }

    public static String formatNaira(long kobo) {
        return koboToNaira(kobo).toPlainString();
    }
}
