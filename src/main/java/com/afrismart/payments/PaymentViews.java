package com.afrismart.payments;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PaymentViews {

    private PaymentViews() {
    }

    public record WalletResponse(UUID ownerId, long balanceKobo, String balance, String currency) {
    }

    public record TransactionResponse(
            UUID id, String type, UUID referenceId, String description,
            long amountKobo, String amount, Instant createdAt
    ) {
    }

    public record OrderPaymentResponse(
            UUID orderId, String status, long amountKobo, String amount,
            long commissionKobo, String commission, long netKobo, String net
    ) {
    }

    public record MerchantSummary(
            long walletBalanceKobo, String walletBalance,
            long inEscrowKobo, String inEscrow,
            long releasedGrossKobo, String releasedGross,
            long commissionKobo, String commission,
            long paidOutKobo, String paidOut,
            List<PayoutResponse> payouts
    ) {
    }

    public record ConsumerSummary(long inEscrowKobo, String inEscrow, int heldOrders) {
    }

    public record PayoutResponse(UUID id, long amountKobo, String amount, String status, String bankLabel, Instant createdAt) {
    }
}
