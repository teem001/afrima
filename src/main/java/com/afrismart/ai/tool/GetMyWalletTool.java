package com.afrismart.ai.tool;

import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.afrismart.payments.PaymentService;
import com.afrismart.payments.PaymentViews;
import com.afrismart.shared.security.AuthUser;
import com.afrismart.shared.security.Role;

@Component
public class GetMyWalletTool implements AiTool {

    private final PaymentService payments;

    public GetMyWalletTool(PaymentService payments) {
        this.payments = payments;
    }

    @Override
    public AiToolType type() {
        return AiToolType.GET_MY_WALLET;
    }

    @Override
    public String description() {
        return "Get the signed-in user's demo wallet balance. For merchants also shows money held in escrow, released earnings and payouts.";
    }

    @Override
    public JsonSchema parameters() {
        return JsonSchema.object(Map.of());
    }

    @Override
    public Set<Role> roles() {
        return Set.of(Role.CONSUMER, Role.MERCHANT);
    }

    @Override
    public ToolResult execute(ToolArgs args, AiToolContext context) {
        AuthUser user = context.user();
        PaymentViews.WalletResponse wallet = payments.wallet(user);
        if (user.role() == Role.MERCHANT) {
            return ToolResult.ok(new MerchantWallet(wallet, payments.merchantSummary(user)));
        }
        return ToolResult.ok(new ConsumerWallet(wallet, payments.consumerSummary(user)));
    }

    public record ConsumerWallet(PaymentViews.WalletResponse wallet, PaymentViews.ConsumerSummary escrow) {
    }

    public record MerchantWallet(PaymentViews.WalletResponse wallet, PaymentViews.MerchantSummary summary) {
    }
}
