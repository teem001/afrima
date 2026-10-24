export function formatNaira(kobo) {
    return (kobo / 100).toFixed(2);
}
export function walletAccount(userId) {
    return `WALLET:${userId}`;
}
export function escrowAccount(orderId) {
    return `ESCROW:${orderId}`;
}
export const EXTERNAL_ACCOUNT = "EXTERNAL";
export const PLATFORM_ACCOUNT = "PLATFORM";
//# sourceMappingURL=money.js.map