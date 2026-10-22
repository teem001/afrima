package com.afrismart.realtime;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import com.afrismart.inventory.StockChangedEvent;
import com.afrismart.orders.OrderEvent;
import com.afrismart.orders.OrderResponse;
import com.afrismart.stores.StoreService;

/** Pushes domain changes to connected clients once the transaction that made them has committed. */
@Component
class RealtimeEventRelay {

    private static final Logger log = LoggerFactory.getLogger(RealtimeEventRelay.class);

    private final SessionRegistry sessions;
    private final StoreService storeService;

    RealtimeEventRelay(SessionRegistry sessions, StoreService storeService) {
        this.sessions = sessions;
        this.storeService = storeService;
    }

    @TransactionalEventListener(fallbackExecution = true)
    void onOrder(OrderEvent event) {
        OrderResponse order = event.order();
        String name = event.kind() == OrderEvent.Kind.PLACED ? "order.placed" : "order.updated";
        sessions.sendToUser(order.consumerId(), name, order);
        try {
            UUID merchantId = storeService.get(order.storeId()).merchantId();
            if (!merchantId.equals(order.consumerId())) {
                sessions.sendToUser(merchantId, name, order);
            }
        } catch (RuntimeException ex) {
            log.warn("Could not notify merchant of order {}: {}", order.id(), ex.getMessage());
        }
    }

    @TransactionalEventListener(fallbackExecution = true)
    void onStock(StockChangedEvent event) {
        sessions.broadcast("stock.changed", event);
    }
}
