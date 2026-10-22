package com.afrismart.orders;

/** Published inside the order transaction; listeners that notify users should run after commit. */
public record OrderEvent(Kind kind, OrderResponse order) {

    public enum Kind {
        PLACED,
        STATUS_CHANGED
    }
}
