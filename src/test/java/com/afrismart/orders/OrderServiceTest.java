package com.afrismart.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.afrismart.inventory.InventoryService;
import com.afrismart.inventory.ReservedStock;
import com.afrismart.payments.PaymentService;
import com.afrismart.shared.api.ApiException;
import com.afrismart.shared.security.AuthUser;
import com.afrismart.shared.security.Role;
import com.afrismart.stores.Store;
import com.afrismart.stores.StoreService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceTest {

    @Mock
    private CustomerOrderRepository orders;
    @Mock
    private InventoryService inventoryService;
    @Mock
    private StoreService storeService;
    @Mock
    private PaymentService payments;
    @Mock
    private AnchorService anchors;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orders, inventoryService, storeService, event -> { }, payments, anchors);
        when(orders.save(any(CustomerOrder.class))).thenAnswer(invocation -> {
            CustomerOrder order = invocation.getArgument(0);
            if (order.getId() == null) {
                order.setId(UUID.randomUUID());
            }
            return order;
        });
    }

    @Test
    void placeReservesStockAndWritesLedgerHash() {
        UUID storeId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        AuthUser consumer = new AuthUser(UUID.randomUUID(), "ada@afrismart.ng", Role.CONSUMER);
        Store store = activeStore(storeId);
        when(storeService.require(storeId)).thenReturn(store);
        when(inventoryService.reserve(eq(storeId), any())).thenReturn(List.of(
                new ReservedStock(productId, 2, 75_000, "NGN")
        ));

        OrderResponse response = orderService.place(
                consumer,
                new PlaceOrderRequest(storeId, List.of(new PlaceOrderRequest.Item(productId, 2)))
        );

        assertThat(response.totalKobo()).isEqualTo(150_000);
        assertThat(response.ledgerHash()).hasSize(64).isNotEqualTo("pending");
        assertThat(response.status()).isEqualTo(OrderStatus.PLACED);
        ArgumentCaptor<List<InventoryService.OrderLineRequest>> captor = ArgumentCaptor.captor();
        verify(inventoryService).reserve(eq(storeId), captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().getFirst().quantity()).isEqualTo(2);
    }

    @Test
    void placeMergesDuplicateProductLinesBeforeReserve() {
        UUID storeId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        AuthUser consumer = new AuthUser(UUID.randomUUID(), "ada@afrismart.ng", Role.CONSUMER);
        when(storeService.require(storeId)).thenReturn(activeStore(storeId));
        when(inventoryService.reserve(eq(storeId), any())).thenReturn(List.of(
                new ReservedStock(productId, 3, 75_000, "NGN")
        ));

        orderService.place(consumer, new PlaceOrderRequest(storeId, List.of(
                new PlaceOrderRequest.Item(productId, 1),
                new PlaceOrderRequest.Item(productId, 2)
        )));

        ArgumentCaptor<List<InventoryService.OrderLineRequest>> captor = ArgumentCaptor.captor();
        verify(inventoryService).reserve(eq(storeId), captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().getFirst().quantity()).isEqualTo(3);
    }

    @Test
    void merchantCannotPlaceConsumerOrder() {
        AuthUser merchant = new AuthUser(UUID.randomUUID(), "yaba@afrismart.ng", Role.MERCHANT);
        assertThatThrownBy(() -> orderService.place(
                merchant,
                new PlaceOrderRequest(UUID.randomUUID(), List.of(new PlaceOrderRequest.Item(UUID.randomUUID(), 1)))
        )).isInstanceOf(ApiException.class);
    }

    private Store activeStore(UUID storeId) {
        Store store = new Store();
        store.setId(storeId);
        store.setActive(true);
        store.setOpenAt(LocalTime.of(8, 0));
        store.setCloseAt(LocalTime.of(20, 0));
        return store;
    }
}
