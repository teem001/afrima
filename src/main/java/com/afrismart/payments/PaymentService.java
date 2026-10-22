package com.afrismart.payments;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.afrismart.shared.api.ApiException;
import com.afrismart.shared.api.ErrorCodes;
import com.afrismart.shared.money.Money;
import com.afrismart.shared.security.AuthUser;

/**
 * Demo-money wallet with a double-entry ledger. Every movement writes balanced debit/credit entries;
 * wallet balances are the cached sum of the owner's WALLET account.
 */
@Service
public class PaymentService {

    public static final long COMMISSION_BPS = 500; // 5%
    public static final String PLATFORM_ACCOUNT = "PLATFORM";
    public static final String EXTERNAL_ACCOUNT = "EXTERNAL";

    private final WalletRepository wallets;
    private final LedgerTransactionRepository ledger;
    private final EscrowHoldRepository escrows;
    private final PayoutRepository payouts;

    public PaymentService(
            WalletRepository wallets,
            LedgerTransactionRepository ledger,
            EscrowHoldRepository escrows,
            PayoutRepository payouts
    ) {
        this.wallets = wallets;
        this.ledger = ledger;
        this.escrows = escrows;
        this.payouts = payouts;
    }

    public static String walletAccount(UUID ownerId) {
        return "WALLET:" + ownerId;
    }

    public static String escrowAccount(UUID orderId) {
        return "ESCROW:" + orderId;
    }

    @Transactional
    public PaymentViews.WalletResponse wallet(AuthUser user) {
        return view(getOrCreate(user.id()));
    }

    @Transactional
    public PaymentViews.WalletResponse fundDemo(AuthUser user, long amountKobo) {
        if (amountKobo < 100 || amountKobo > 100_000_000) {
            throw ApiException.badRequest(ErrorCodes.VALIDATION_FAILED, "Demo funding must be between ₦1 and ₦1,000,000");
        }
        Wallet wallet = getOrCreate(user.id());
        wallet.setBalanceKobo(wallet.getBalanceKobo() + amountKobo);
        wallets.save(wallet);
        record("DEMO_FUND", null, "Demo wallet funding",
                entry(EXTERNAL_ACCOUNT, amountKobo, 0),
                entry(walletAccount(user.id()), 0, amountKobo));
        return view(wallet);
    }

    @Transactional(readOnly = true)
    public List<PaymentViews.TransactionResponse> transactions(AuthUser user) {
        String account = walletAccount(user.id());
        return ledger.findByAccount(account).stream().limit(50).map(tx -> {
            long signed = tx.getEntries().stream()
                    .filter(e -> e.getAccount().equals(account))
                    .mapToLong(e -> e.getCreditKobo() - e.getDebitKobo())
                    .sum();
            return new PaymentViews.TransactionResponse(
                    tx.getId(), tx.getType(), tx.getReferenceId(), tx.getDescription(),
                    signed, Money.formatNaira(signed), tx.getCreatedAt());
        }).toList();
    }

    /** Debits the consumer wallet into a per-order escrow hold. Runs inside the order transaction. */
    @Transactional
    public void hold(UUID consumerId, UUID merchantId, UUID storeId, UUID orderId, long amountKobo) {
        if (escrows.findByOrderId(orderId).isPresent()) {
            return; // idempotent: a retried placement must not double-hold
        }
        Wallet wallet = getOrCreate(consumerId);
        if (wallet.getBalanceKobo() < amountKobo) {
            throw ApiException.badRequest(ErrorCodes.INSUFFICIENT_FUNDS,
                    "Wallet balance is too low for this order. Add demo funds to your wallet first.");
        }
        wallet.setBalanceKobo(wallet.getBalanceKobo() - amountKobo);
        wallets.save(wallet);
        EscrowHold hold = new EscrowHold();
        hold.setOrderId(orderId);
        hold.setConsumerId(consumerId);
        hold.setMerchantId(merchantId);
        hold.setStoreId(storeId);
        hold.setAmountKobo(amountKobo);
        escrows.save(hold);
        record("ESCROW_HOLD", orderId, "Payment held in escrow",
                entry(walletAccount(consumerId), amountKobo, 0),
                entry(escrowAccount(orderId), 0, amountKobo));
    }

    /** Releases escrow to the merchant wallet minus commission. Runs inside the fulfil transaction. */
    @Transactional
    public void release(UUID orderId) {
        EscrowHold hold = escrows.findByOrderId(orderId).orElse(null);
        if (hold == null || hold.getStatus() != EscrowHold.Status.HELD) {
            return;
        }
        long commission = hold.getAmountKobo() * COMMISSION_BPS / 10_000;
        long net = hold.getAmountKobo() - commission;
        hold.setCommissionKobo(commission);
        hold.setStatus(EscrowHold.Status.RELEASED);
        escrows.save(hold);
        Wallet merchant = getOrCreate(hold.getMerchantId());
        merchant.setBalanceKobo(merchant.getBalanceKobo() + net);
        wallets.save(merchant);
        record("ESCROW_RELEASE", orderId, "Escrow released to merchant (5% commission)",
                entry(escrowAccount(orderId), hold.getAmountKobo(), 0),
                entry(walletAccount(hold.getMerchantId()), 0, net),
                entry(PLATFORM_ACCOUNT, 0, commission));
    }

    /** Refunds a held escrow back to the consumer wallet (order cancelled before fulfilment). */
    @Transactional
    public void refund(UUID orderId) {
        EscrowHold hold = escrows.findByOrderId(orderId).orElse(null);
        if (hold == null || hold.getStatus() != EscrowHold.Status.HELD) {
            return;
        }
        hold.setStatus(EscrowHold.Status.REFUNDED);
        escrows.save(hold);
        Wallet consumer = getOrCreate(hold.getConsumerId());
        consumer.setBalanceKobo(consumer.getBalanceKobo() + hold.getAmountKobo());
        wallets.save(consumer);
        record("ESCROW_REFUND", orderId, "Escrow refunded to consumer",
                entry(escrowAccount(orderId), hold.getAmountKobo(), 0),
                entry(walletAccount(hold.getConsumerId()), 0, hold.getAmountKobo()));
    }

    @Transactional(readOnly = true)
    public PaymentViews.OrderPaymentResponse orderPayment(UUID orderId) {
        return escrows.findByOrderId(orderId).map(hold -> {
            String status = switch (hold.getStatus()) {
                case HELD -> "IN_ESCROW";
                case RELEASED -> "RELEASED";
                case REFUNDED -> "REFUNDED";
            };
            long net = hold.getAmountKobo() - hold.getCommissionKobo();
            return new PaymentViews.OrderPaymentResponse(
                    orderId, status, hold.getAmountKobo(), Money.formatNaira(hold.getAmountKobo()),
                    hold.getCommissionKobo(), Money.formatNaira(hold.getCommissionKobo()),
                    net, Money.formatNaira(net));
        }).orElseGet(() -> new PaymentViews.OrderPaymentResponse(
                orderId, "UNPAID", 0, Money.formatNaira(0), 0, Money.formatNaira(0), 0, Money.formatNaira(0)));
    }

    @Transactional
    public PaymentViews.MerchantSummary merchantSummary(AuthUser merchant) {
        Wallet wallet = getOrCreate(merchant.id());
        List<EscrowHold> all = escrows.findByMerchantId(merchant.id());
        long held = all.stream().filter(h -> h.getStatus() == EscrowHold.Status.HELD).mapToLong(EscrowHold::getAmountKobo).sum();
        long releasedGross = all.stream().filter(h -> h.getStatus() == EscrowHold.Status.RELEASED).mapToLong(EscrowHold::getAmountKobo).sum();
        long commission = all.stream().filter(h -> h.getStatus() == EscrowHold.Status.RELEASED).mapToLong(EscrowHold::getCommissionKobo).sum();
        List<Payout> mine = payouts.findByMerchantIdOrderByCreatedAtDesc(merchant.id());
        long paidOut = mine.stream().mapToLong(Payout::getAmountKobo).sum();
        return new PaymentViews.MerchantSummary(
                wallet.getBalanceKobo(), Money.formatNaira(wallet.getBalanceKobo()),
                held, Money.formatNaira(held),
                releasedGross, Money.formatNaira(releasedGross),
                commission, Money.formatNaira(commission),
                paidOut, Money.formatNaira(paidOut),
                mine.stream().map(PaymentService::payoutView).toList());
    }

    @Transactional(readOnly = true)
    public PaymentViews.ConsumerSummary consumerSummary(AuthUser consumer) {
        List<EscrowHold> held = escrows.findByConsumerIdAndStatus(consumer.id(), EscrowHold.Status.HELD);
        long sum = held.stream().mapToLong(EscrowHold::getAmountKobo).sum();
        return new PaymentViews.ConsumerSummary(sum, Money.formatNaira(sum), held.size());
    }

    @Transactional
    public PaymentViews.PayoutResponse requestPayout(AuthUser merchant, long amountKobo) {
        if (amountKobo < 100) {
            throw ApiException.badRequest(ErrorCodes.VALIDATION_FAILED, "Payout must be at least ₦1");
        }
        Wallet wallet = getOrCreate(merchant.id());
        if (wallet.getBalanceKobo() < amountKobo) {
            throw ApiException.badRequest(ErrorCodes.INSUFFICIENT_FUNDS, "Payout exceeds your available wallet balance");
        }
        wallet.setBalanceKobo(wallet.getBalanceKobo() - amountKobo);
        wallets.save(wallet);
        Payout payout = new Payout();
        payout.setMerchantId(merchant.id());
        payout.setAmountKobo(amountKobo);
        payouts.save(payout);
        record("PAYOUT", payout.getId(), "Simulated payout to demo bank account",
                entry(walletAccount(merchant.id()), amountKobo, 0),
                entry(EXTERNAL_ACCOUNT, 0, amountKobo));
        return payoutView(payout);
    }

    @Transactional(readOnly = true)
    public List<PaymentViews.PayoutResponse> myPayouts(AuthUser merchant) {
        return payouts.findByMerchantIdOrderByCreatedAtDesc(merchant.id()).stream().map(PaymentService::payoutView).toList();
    }

    private Wallet getOrCreate(UUID ownerId) {
        return wallets.findByOwnerId(ownerId).orElseGet(() -> {
            Wallet wallet = new Wallet();
            wallet.setOwnerId(ownerId);
            return wallets.save(wallet);
        });
    }

    private void record(String type, UUID referenceId, String description, LedgerEntry... entries) {
        long debits = 0;
        long credits = 0;
        LedgerTransaction tx = new LedgerTransaction();
        tx.setType(type);
        tx.setReferenceId(referenceId);
        tx.setDescription(description);
        for (LedgerEntry entry : entries) {
            debits += entry.getDebitKobo();
            credits += entry.getCreditKobo();
            tx.addEntry(entry);
        }
        if (debits != credits) {
            throw new IllegalStateException("Unbalanced ledger transaction: " + type);
        }
        ledger.save(tx);
    }

    private static LedgerEntry entry(String account, long debitKobo, long creditKobo) {
        LedgerEntry entry = new LedgerEntry();
        entry.setAccount(account);
        entry.setDebitKobo(debitKobo);
        entry.setCreditKobo(creditKobo);
        return entry;
    }

    private static PaymentViews.PayoutResponse payoutView(Payout payout) {
        return new PaymentViews.PayoutResponse(
                payout.getId(), payout.getAmountKobo(), Money.formatNaira(payout.getAmountKobo()),
                payout.getStatus(), payout.getBankLabel(), payout.getCreatedAt());
    }

    private static PaymentViews.WalletResponse view(Wallet wallet) {
        return new PaymentViews.WalletResponse(
                wallet.getOwnerId(), wallet.getBalanceKobo(), Money.formatNaira(wallet.getBalanceKobo()), wallet.getCurrency());
    }
}
