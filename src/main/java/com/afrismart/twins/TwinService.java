package com.afrismart.twins;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.afrismart.catalog.CatalogService;
import com.afrismart.identity.AuthService;
import com.afrismart.catalog.ProductResponse;
import com.afrismart.inventory.InventoryItemResponse;
import com.afrismart.inventory.InventoryService;
import com.afrismart.orders.OrderResponse;
import com.afrismart.orders.OrderService;
import com.afrismart.orders.OrderStatus;
import com.afrismart.orders.SalesTally;
import com.afrismart.payments.PaymentService;
import com.afrismart.payments.PaymentViews;
import com.afrismart.shared.money.Money;
import com.afrismart.shared.security.AuthUser;
import com.afrismart.stores.Store;
import com.afrismart.stores.StoreService;

/** Twins are projections over live state — no separate twin store to drift out of sync. */
@Service
public class TwinService {

    static final int LOW_STOCK_THRESHOLD = 10;

    private final StoreService storeService;
    private final InventoryService inventoryService;
    private final CatalogService catalogService;
    private final OrderService orderService;
    private final PaymentService payments;
    private final AuthService authService;

    public TwinService(
            StoreService storeService,
            InventoryService inventoryService,
            CatalogService catalogService,
            OrderService orderService,
            PaymentService payments,
            AuthService authService
    ) {
        this.storeService = storeService;
        this.inventoryService = inventoryService;
        this.catalogService = catalogService;
        this.orderService = orderService;
        this.payments = payments;
        this.authService = authService;
    }

    @Transactional
    public TwinViews.ShopTwin shopTwin(AuthUser merchant, UUID storeId) {
        Store store = storeService.requireOwned(storeId, merchant.id());
        List<TwinViews.StockTwin> stock = stockTwins(storeId);
        List<TwinViews.Suggestion> suggestions = new ArrayList<>();
        for (TwinViews.StockTwin item : stock) {
            if (item.lowStock() || item.quantity() == 0) {
                int suggested = (int) Math.max(10, Math.round(item.velocityPerDay() * 14) - item.quantity());
                String when = item.quantity() == 0
                        ? " — out of stock"
                        : item.daysToStockout() == null
                        ? ""
                        : String.format(" (about %.0f day(s) of stock left)", Math.floor(item.daysToStockout()));
                suggestions.add(new TwinViews.Suggestion(
                        "RESTOCK",
                        "Add " + suggested + " × " + item.productName() + when + ". Tap Restock to put them on the shelf.",
                        item.productId(),
                        suggested
                ));
            }
        }
        PaymentViews.MerchantSummary money = payments.merchantSummary(merchant);
        if (money.walletBalanceKobo() > 0) {
            suggestions.add(new TwinViews.Suggestion("PAYOUT",
                    "You have " + money.walletBalance() + " available — you can request a payout from the Payouts tab."));
        }
        if (money.inEscrowKobo() > 0) {
            suggestions.add(new TwinViews.Suggestion("ESCROW",
                    money.inEscrow() + " is held in escrow and releases when those orders are fulfilled."));
        }
        var storeView = storeService.get(storeId);
        return new TwinViews.ShopTwin(
                store.getId(), store.getName(), store.getAddress(), storeView.openNow(), storeView.imageUrl(),
                stock, suggestions, money);
    }

    @Transactional
    public TwinViews.CustomerTwin customerTwin(AuthUser user) {
        List<OrderResponse> orders = orderService.listMine(user);
        long spent = orders.stream()
                .filter(o -> o.status() != OrderStatus.CANCELLED)
                .mapToLong(OrderResponse::totalKobo)
                .sum();
        return new TwinViews.CustomerTwin(
                user.id(), user.email(), authService.me(user).fullName(), user.role().name(),
                payments.wallet(user), payments.consumerSummary(user),
                orders.size(), spent, Money.formatNaira(spent),
                orders.stream().limit(5).toList());
    }

    @Transactional(readOnly = true)
    public TwinViews.Simulation simulate(AuthUser merchant, TwinViews.SimulationRequest request) {
        storeService.requireOwned(request.storeId(), merchant.id());
        InventoryItemResponse item = inventoryService.find(request.storeId(), request.productId())
                .orElseThrow(() -> com.afrismart.shared.api.ApiException.notFound(
                        com.afrismart.shared.api.ErrorCodes.PRODUCT_NOT_FOUND, "Product is not stocked at this store"));
        ProductResponse product = catalogService.get(request.productId());
        long sold7d = soldLast7Days(request.storeId(), request.productId());
        double velocity = sold7d / 7.0;
        int restock = request.restockQty() == null ? 0 : Math.max(0, request.restockQty());
        int pct = request.priceChangePercent() == null ? 0 : Math.max(-90, Math.min(200, request.priceChangePercent()));
        long simulatedPrice = Math.max(0, item.unitPriceKobo() * (100 + pct) / 100);
        Double daysNow = velocity > 0 ? item.quantity() / velocity : null;
        Double daysAfter = velocity > 0 ? (item.quantity() + restock) / velocity : null;
        long projected = Math.round(velocity * 7 * item.unitPriceKobo());
        long simulated = Math.round(velocity * 7 * simulatedPrice);
        return new TwinViews.Simulation(
                item.productId(), product.name(), item.quantity(), restock,
                item.unitPriceKobo(), Money.formatNaira(item.unitPriceKobo()),
                simulatedPrice, Money.formatNaira(simulatedPrice),
                round2(velocity), daysNow == null ? null : round2(daysNow), daysAfter == null ? null : round2(daysAfter),
                projected, Money.formatNaira(projected), simulated, Money.formatNaira(simulated),
                "Projection assumes the last 7 days' sales rate stays constant; simulation never changes real stock or prices.");
    }

    private List<TwinViews.StockTwin> stockTwins(UUID storeId) {
        List<InventoryItemResponse> items = inventoryService.listByStore(storeId);
        Map<UUID, ProductResponse> products = catalogService.getAll(items.stream().map(InventoryItemResponse::productId).toList());
        Map<UUID, Long> sold = new java.util.HashMap<>();
        for (SalesTally tally : orderService.unitsSoldSince(List.of(storeId), Instant.now().minus(7, ChronoUnit.DAYS))) {
            sold.put(tally.productId(), tally.units() == null ? 0 : tally.units());
        }
        return items.stream().map(item -> {
            ProductResponse product = products.get(item.productId());
            long sold7d = sold.getOrDefault(item.productId(), 0L);
            double velocity = sold7d / 7.0;
            Double days = velocity > 0 ? round2(item.quantity() / velocity) : null;
            boolean low = item.quantity() <= LOW_STOCK_THRESHOLD || (days != null && days < 7);
            return new TwinViews.StockTwin(
                    item.productId(),
                    product == null ? "Unknown product" : product.name(),
                    product == null ? "" : product.sku(),
                    product == null ? null : product.imageUrl(),
                    item.quantity(), item.unitPriceKobo(), Money.formatNaira(item.unitPriceKobo()),
                    sold7d, round2(velocity), days, low);
        }).toList();
    }

    private long soldLast7Days(UUID storeId, UUID productId) {
        return orderService.unitsSoldSince(List.of(storeId), Instant.now().minus(7, ChronoUnit.DAYS)).stream()
                .filter(t -> t.productId().equals(productId))
                .mapToLong(t -> t.units() == null ? 0 : t.units())
                .sum();
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
