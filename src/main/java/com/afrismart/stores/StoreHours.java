package com.afrismart.stores;

import java.time.LocalTime;

public final class StoreHours {

    private StoreHours() {
    }

    public static boolean isOpen(LocalTime openAt, LocalTime closeAt, LocalTime now) {
        if (openAt.equals(closeAt)) {
            return true;
        }
        if (openAt.isBefore(closeAt)) {
            return !now.isBefore(openAt) && now.isBefore(closeAt);
        }
        return !now.isBefore(openAt) || now.isBefore(closeAt);
    }
}
