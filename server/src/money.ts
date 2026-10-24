export function formatNaira(kobo: number): string {
  return (kobo / 100).toFixed(2);
}

export function walletAccount(userId: string) {
  return `WALLET:${userId}`;
}
export function escrowAccount(orderId: string) {
  return `ESCROW:${orderId}`;
}
export const EXTERNAL_ACCOUNT = "EXTERNAL";
export const PLATFORM_ACCOUNT = "PLATFORM";
