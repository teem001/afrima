package com.afrismart.payments;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.afrismart.shared.security.AuthUser;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/v1")
public class PaymentController {

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    public record FundRequest(@NotNull @Min(100) @Max(100_000_000) Long amountKobo) {
    }

    public record PayoutRequestBody(@NotNull @Min(100) @Max(100_000_000) Long amountKobo) {
    }

    @GetMapping("/wallet")
    public PaymentViews.WalletResponse wallet() {
        return payments.wallet(AuthUser.require());
    }

    @PostMapping("/wallet/fund")
    public PaymentViews.WalletResponse fund(@Valid @RequestBody FundRequest request) {
        return payments.fundDemo(AuthUser.require(), request.amountKobo());
    }

    @GetMapping("/wallet/transactions")
    public List<PaymentViews.TransactionResponse> transactions() {
        return payments.transactions(AuthUser.require());
    }

    @GetMapping("/payments/orders/{orderId}")
    public PaymentViews.OrderPaymentResponse orderPayment(@PathVariable UUID orderId) {
        return payments.orderPayment(orderId);
    }

    @GetMapping("/merchant/payments/summary")
    @PreAuthorize("hasRole('MERCHANT')")
    public PaymentViews.MerchantSummary merchantSummary() {
        return payments.merchantSummary(AuthUser.require());
    }

    @PostMapping("/merchant/payouts")
    @PreAuthorize("hasRole('MERCHANT')")
    public PaymentViews.PayoutResponse requestPayout(@Valid @RequestBody PayoutRequestBody request) {
        return payments.requestPayout(AuthUser.require(), request.amountKobo());
    }

    @GetMapping("/merchant/payouts")
    @PreAuthorize("hasRole('MERCHANT')")
    public List<PaymentViews.PayoutResponse> payouts() {
        return payments.myPayouts(AuthUser.require());
    }
}
