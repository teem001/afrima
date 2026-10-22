package com.afrismart.twins;

import java.util.List;
import java.util.UUID;

import com.afrismart.orders.OrderResponse;
import com.afrismart.payments.PaymentViews;

public final class TwinViews {

    private TwinViews() {
    }

    public record StockTwin(
            UUID productId, String productName, String sku, int quantity,
            long unitPriceKobo, String unitPrice,
            long unitsSold7d, double velocityPerDay, Double daysToStockout, boolean lowStock
    ) {
    }

    public record Suggestion(String type, String message) {
    }

    public record ShopTwin(
            UUID storeId, String storeName, String address, boolean openNow,
            List<StockTwin> stock, List<Suggestion> suggestions,
            PaymentViews.MerchantSummary money
    ) {
    }

    public record CustomerTwin(
            UUID userId, String email, String fullName, String role,
            PaymentViews.WalletResponse wallet, PaymentViews.ConsumerSummary escrow,
            int ordersCount, long totalSpentKobo, String totalSpent,
            List<OrderResponse> recentOrders
    ) {
    }

    public record SimulationRequest(UUID storeId, UUID productId, Integer restockQty, Integer priceChangePercent) {
    }

    public record Simulation(
            UUID productId, String productName, int currentQuantity, int restockQty,
            long currentPriceKobo, String currentPrice, long simulatedPriceKobo, String simulatedPrice,
            double velocityPerDay, Double daysToStockoutNow, Double daysToStockoutAfterRestock,
            long projectedRevenue7dKobo, String projectedRevenue7d,
            long simulatedRevenue7dKobo, String simulatedRevenue7d,
            String note
    ) {
    }
}
