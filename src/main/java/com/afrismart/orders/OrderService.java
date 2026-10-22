package com.afrismart.orders;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.afrismart.inventory.InventoryService;
import com.afrismart.inventory.ReservedStock;
import com.afrismart.payments.PaymentService;
import com.afrismart.shared.api.ApiException;
import com.afrismart.shared.api.ErrorCodes;
import com.afrismart.shared.security.AuthUser;
import com.afrismart.shared.security.Role;
import com.afrismart.stores.Store;
import com.afrismart.stores.StoreService;

@Service
public class OrderService {

    private final CustomerOrderRepository orders;
    private final InventoryService inventoryService;
    private final StoreService storeService;
    private final ApplicationEventPublisher events;
    private final PaymentService payments;
    private final AnchorService anchors;

    public OrderService(
            CustomerOrderRepository orders,
            InventoryService inventoryService,
            StoreService storeService,
            ApplicationEventPublisher events,
            PaymentService payments,
            AnchorService anchors
    ) {
        this.orders = orders;
        this.inventoryService = inventoryService;
        this.storeService = storeService;
        this.events = events;
        this.payments = payments;
        this.anchors = anchors;
    }

    @Transactional
    public OrderResponse place(AuthUser consumer, PlaceOrderRequest request) {
        if (consumer.role() != Role.CONSUMER) {
            throw ApiException.forbidden("FORBIDDEN", "Consumer role required");
        }
        Store store = storeService.require(request.storeId());
        if (!store.isActive()) {
            throw ApiException.badRequest(ErrorCodes.STORE_NOT_FOUND, "Store is not accepting orders");
        }
        List<InventoryService.OrderLineRequest> lines = mergeLines(request.items());
        List<ReservedStock> reserved = inventoryService.reserve(store.getId(), lines);

        CustomerOrder order = new CustomerOrder();
        order.setConsumerId(consumer.id());
        order.setStoreId(store.getId());
        order.setStatus(OrderStatus.PLACED);
        order.setCurrency(reserved.getFirst().currency());
        order.setLedgerHash("pending");
        long total = 0;
        for (ReservedStock stock : reserved) {
            OrderLine line = new OrderLine();
            line.setProductId(stock.productId());
            line.setQuantity(stock.quantity());
            line.setUnitPriceKobo(stock.unitPriceKobo());
            order.addLine(line);
            total += stock.unitPriceKobo() * stock.quantity();
        }
        order.setTotalKobo(total);
        orders.save(order);

        Instant createdAt = order.getCreatedAt() == null ? Instant.now() : order.getCreatedAt();
        order.setLedgerHash(LedgerHasher.hash(
                order.getId(),
                order.getConsumerId(),
                order.getStoreId(),
                order.getTotalKobo(),
                order.getCurrency(),
                createdAt,
                order.getLines().stream()
                        .map(line -> new LedgerHasher.Line(line.getProductId(), line.getQuantity(), line.getUnitPriceKobo()))
                        .toList()
        ));
        // Pay from the consumer wallet into escrow; insufficient funds roll the whole order (incl. stock) back.
        payments.hold(consumer.id(), store.getMerchantId(), store.getId(), order.getId(), order.getTotalKobo());
        OrderResponse response = OrderResponse.from(order);
        events.publishEvent(new OrderEvent(OrderEvent.Kind.PLACED, response));
        return response;
    }

    /** Merchant marks the order handed over: escrow releases (minus commission) and the hash is anchored. */
    @Transactional
    public OrderResponse fulfil(AuthUser merchant, UUID orderId) {
        CustomerOrder order = requireVisibleToMerchant(merchant, orderId);
        if (order.getStatus() != OrderStatus.ACCEPTED) {
            throw ApiException.conflict(ErrorCodes.INVALID_ORDER_STATE, "Only accepted orders can be fulfilled");
        }
        order.setStatus(OrderStatus.FULFILLED);
        payments.release(orderId);
        anchors.anchorOrder(order);
        OrderResponse response = OrderResponse.from(order);
        events.publishEvent(new OrderEvent(OrderEvent.Kind.STATUS_CHANGED, response));
        return response;
    }

    /** Consumer or owning merchant cancels before fulfilment: stock is restored and escrow refunded. */
    @Transactional
    public OrderResponse cancel(AuthUser user, UUID orderId) {
        CustomerOrder order = orders.findById(orderId)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.ORDER_NOT_FOUND, "Order not found"));
        assertCanView(user, order);
        if (order.getStatus() == OrderStatus.FULFILLED || order.getStatus() == OrderStatus.CANCELLED) {
            throw ApiException.conflict(ErrorCodes.INVALID_ORDER_STATE, "This order can no longer be cancelled");
        }
        order.setStatus(OrderStatus.CANCELLED);
        inventoryService.restore(order.getStoreId(), order.getLines().stream()
                .map(line -> new InventoryService.OrderLineRequest(line.getProductId(), line.getQuantity()))
                .toList());
        payments.refund(orderId);
        OrderResponse response = OrderResponse.from(order);
        events.publishEvent(new OrderEvent(OrderEvent.Kind.STATUS_CHANGED, response));
        return response;
    }

    @Transactional
    public OrderResponse accept(AuthUser merchant, UUID orderId) {
        CustomerOrder order = requireVisibleToMerchant(merchant, orderId);
        if (order.getStatus() != OrderStatus.PLACED) {
            throw ApiException.conflict(ErrorCodes.INVALID_ORDER_STATE, "Only placed orders can be accepted");
        }
        order.setStatus(OrderStatus.ACCEPTED);
        OrderResponse response = OrderResponse.from(order);
        events.publishEvent(new OrderEvent(OrderEvent.Kind.STATUS_CHANGED, response));
        return response;
    }

    @Transactional(readOnly = true)
    public OrderResponse get(AuthUser user, UUID orderId) {
        CustomerOrder order = orders.findById(orderId)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.ORDER_NOT_FOUND, "Order not found"));
        assertCanView(user, order);
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listMine(AuthUser consumer) {
        return orders.findByConsumerIdOrderByCreatedAtDesc(consumer.id()).stream()
                .map(OrderResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listForMerchant(AuthUser merchant) {
        if (merchant.role() != Role.MERCHANT) {
            throw ApiException.forbidden("FORBIDDEN", "Merchant role required");
        }
        List<UUID> storeIds = storeService.listMine(merchant).stream()
                .map(store -> store.id())
                .toList();
        if (storeIds.isEmpty()) {
            return List.of();
        }
        return orders.findByStoreIdInOrderByCreatedAtDesc(storeIds).stream()
                .map(OrderResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SalesTally> unitsSoldSince(Collection<UUID> storeIds, Instant since) {
        if (storeIds.isEmpty()) {
            return List.of();
        }
        return orders.unitsSoldSince(storeIds, since, OrderStatus.CANCELLED);
    }

    private CustomerOrder requireVisibleToMerchant(AuthUser merchant, UUID orderId) {
        CustomerOrder order = orders.findById(orderId)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.ORDER_NOT_FOUND, "Order not found"));
        storeService.requireOwned(order.getStoreId(), merchant.id());
        return order;
    }

    private void assertCanView(AuthUser user, CustomerOrder order) {
        if (user.id().equals(order.getConsumerId())) {
            return;
        }
        if (user.role() == Role.MERCHANT) {
            storeService.requireOwned(order.getStoreId(), user.id());
            return;
        }
        throw ApiException.forbidden("FORBIDDEN", "You cannot view this order");
    }

    private List<InventoryService.OrderLineRequest> mergeLines(List<PlaceOrderRequest.Item> items) {
        LinkedHashMap<UUID, Integer> quantities = new LinkedHashMap<>();
        for (PlaceOrderRequest.Item item : items) {
            quantities.merge(item.productId(), item.quantity(), Integer::sum);
        }
        return quantities.entrySet().stream()
                .map(entry -> new InventoryService.OrderLineRequest(entry.getKey(), entry.getValue()))
                .toList();
    }
}
