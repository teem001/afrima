import { v4 as uuid } from "uuid";
import type { AuthUser } from "../auth/jwt.js";
import type { DbClient } from "../db/pool.js";
import { pool, withTx } from "../db/pool.js";
import { ApiError } from "../errors.js";
import {
  EXTERNAL_ACCOUNT,
  PLATFORM_ACCOUNT,
  escrowAccount,
  formatNaira,
  walletAccount,
} from "../money.js";
import { config } from "../config.js";

async function getOrCreateWallet(client: DbClient, ownerId: string) {
  const existing = await client.query(`SELECT * FROM wallets WHERE owner_id = $1`, [ownerId]);
  if (existing.rows[0]) return existing.rows[0];
  const id = uuid();
  const now = new Date();
  await client.query(
    `INSERT INTO wallets (id, owner_id, balance_kobo, currency, created_at, updated_at)
     VALUES ($1,$2,0,'NGN',$3,$3)`,
    [id, ownerId, now],
  );
  const res = await client.query(`SELECT * FROM wallets WHERE id = $1`, [id]);
  return res.rows[0];
}

function walletView(row: { id: string; owner_id: string; balance_kobo: string | number; currency: string }) {
  const balanceKobo = Number(row.balance_kobo);
  return {
    id: row.id,
    ownerId: row.owner_id,
    balanceKobo,
    balance: formatNaira(balanceKobo),
    currency: row.currency,
  };
}

async function record(
  client: DbClient,
  type: string,
  referenceId: string | null,
  description: string,
  entries: { account: string; debit: number; credit: number }[],
) {
  const txId = uuid();
  const now = new Date();
  await client.query(
    `INSERT INTO ledger_transactions (id, type, reference_id, description, created_at, updated_at)
     VALUES ($1,$2,$3,$4,$5,$5)`,
    [txId, type, referenceId, description, now],
  );
  for (const e of entries) {
    await client.query(
      `INSERT INTO ledger_entries (id, transaction_id, account, debit_kobo, credit_kobo, created_at, updated_at)
       VALUES ($1,$2,$3,$4,$5,$6,$6)`,
      [uuid(), txId, e.account, e.debit, e.credit, now],
    );
  }
}

export async function wallet(user: AuthUser) {
  const row = await getOrCreateWallet(pool, user.id);
  return walletView(row);
}

export async function fundDemo(user: AuthUser, amountKobo: number) {
  if (amountKobo < 100 || amountKobo > 100_000_000) {
    throw ApiError.badRequest("VALIDATION_FAILED", "Demo funding must be between ₦1 and ₦1,000,000");
  }
  return withTx(async (client) => {
    const w = await getOrCreateWallet(client, user.id);
    await client.query(`UPDATE wallets SET balance_kobo = balance_kobo + $2, updated_at = NOW() WHERE id = $1`, [
      w.id,
      amountKobo,
    ]);
    await record(client, "DEMO_FUND", null, "Demo wallet funding", [
      { account: EXTERNAL_ACCOUNT, debit: amountKobo, credit: 0 },
      { account: walletAccount(user.id), debit: 0, credit: amountKobo },
    ]);
    const updated = await client.query(`SELECT * FROM wallets WHERE id = $1`, [w.id]);
    return walletView(updated.rows[0]);
  });
}

export async function transactions(user: AuthUser) {
  const account = walletAccount(user.id);
  const res = await pool.query(
    `SELECT t.id, t.type, t.reference_id, t.description, t.created_at,
            COALESCE(SUM(e.credit_kobo - e.debit_kobo), 0) AS signed
     FROM ledger_transactions t
     JOIN ledger_entries e ON e.transaction_id = t.id
     WHERE e.account = $1
     GROUP BY t.id
     ORDER BY t.created_at DESC
     LIMIT 50`,
    [account],
  );
  return res.rows.map((r) => {
    const signed = Number(r.signed);
    return {
      id: r.id,
      type: r.type,
      referenceId: r.reference_id,
      description: r.description,
      amountKobo: signed,
      amount: formatNaira(signed),
      createdAt: r.created_at,
    };
  });
}

export async function hold(
  client: DbClient,
  consumerId: string,
  merchantId: string,
  storeId: string,
  orderId: string,
  amountKobo: number,
) {
  const existing = await client.query(`SELECT 1 FROM escrow_holds WHERE order_id = $1`, [orderId]);
  if (existing.rowCount) return;

  const w = await getOrCreateWallet(client, consumerId);
  if (Number(w.balance_kobo) < amountKobo) {
    throw ApiError.conflict("INSUFFICIENT_FUNDS", "Wallet balance too low");
  }
  await client.query(`UPDATE wallets SET balance_kobo = balance_kobo - $2, updated_at = NOW() WHERE id = $1`, [
    w.id,
    amountKobo,
  ]);
  const commission = Math.floor((amountKobo * config.commissionBps) / 10_000);
  await client.query(
    `INSERT INTO escrow_holds (
      id, order_id, consumer_id, merchant_id, store_id, amount_kobo, commission_kobo, status, created_at, updated_at
    ) VALUES ($1,$2,$3,$4,$5,$6,$7,'HELD',NOW(),NOW())`,
    [uuid(), orderId, consumerId, merchantId, storeId, amountKobo, commission],
  );
  await record(client, "ESCROW_HOLD", orderId, "Order escrow hold", [
    { account: walletAccount(consumerId), debit: amountKobo, credit: 0 },
    { account: escrowAccount(orderId), debit: 0, credit: amountKobo },
  ]);
}

export async function release(client: DbClient, orderId: string) {
  const res = await client.query(`SELECT * FROM escrow_holds WHERE order_id = $1 FOR UPDATE`, [orderId]);
  const hold = res.rows[0];
  if (!hold || hold.status !== "HELD") return;
  const amount = Number(hold.amount_kobo);
  const commission = Number(hold.commission_kobo);
  const net = amount - commission;
  const merchantWallet = await getOrCreateWallet(client, hold.merchant_id);
  await client.query(`UPDATE wallets SET balance_kobo = balance_kobo + $2, updated_at = NOW() WHERE id = $1`, [
    merchantWallet.id,
    net,
  ]);
  await client.query(`UPDATE escrow_holds SET status = 'RELEASED', updated_at = NOW() WHERE id = $1`, [hold.id]);
  await record(client, "ESCROW_RELEASE", orderId, "Escrow released to merchant", [
    { account: escrowAccount(orderId), debit: amount, credit: 0 },
    { account: walletAccount(hold.merchant_id), debit: 0, credit: net },
    { account: PLATFORM_ACCOUNT, debit: 0, credit: commission },
  ]);
}

export async function refund(client: DbClient, orderId: string) {
  const res = await client.query(`SELECT * FROM escrow_holds WHERE order_id = $1 FOR UPDATE`, [orderId]);
  const hold = res.rows[0];
  if (!hold || hold.status !== "HELD") return;
  const amount = Number(hold.amount_kobo);
  const consumerWallet = await getOrCreateWallet(client, hold.consumer_id);
  await client.query(`UPDATE wallets SET balance_kobo = balance_kobo + $2, updated_at = NOW() WHERE id = $1`, [
    consumerWallet.id,
    amount,
  ]);
  await client.query(`UPDATE escrow_holds SET status = 'REFUNDED', updated_at = NOW() WHERE id = $1`, [hold.id]);
  await record(client, "ESCROW_REFUND", orderId, "Escrow refunded", [
    { account: escrowAccount(orderId), debit: amount, credit: 0 },
    { account: walletAccount(hold.consumer_id), debit: 0, credit: amount },
  ]);
}

export async function orderPayment(orderId: string) {
  const res = await pool.query(`SELECT * FROM escrow_holds WHERE order_id = $1`, [orderId]);
  const h = res.rows[0];
  if (!h) {
    return {
      orderId,
      status: "UNPAID",
      amountKobo: 0,
      amount: "0.00",
      commissionKobo: 0,
      commission: "0.00",
      netKobo: 0,
      net: "0.00",
    };
  }
  const amountKobo = Number(h.amount_kobo);
  const commissionKobo = Number(h.commission_kobo);
  const netKobo = amountKobo - commissionKobo;
  const status = h.status === "HELD" ? "IN_ESCROW" : h.status === "RELEASED" ? "RELEASED" : "REFUNDED";
  return {
    orderId,
    status,
    amountKobo,
    amount: formatNaira(amountKobo),
    commissionKobo,
    commission: formatNaira(commissionKobo),
    netKobo,
    net: formatNaira(netKobo),
  };
}

export async function consumerSummary(user: AuthUser) {
  const held = await pool.query(
    `SELECT COALESCE(SUM(amount_kobo),0) AS held, COUNT(*)::int AS n
     FROM escrow_holds WHERE consumer_id = $1 AND status = 'HELD'`,
    [user.id],
  );
  const inEscrowKobo = Number(held.rows[0].held);
  return {
    inEscrowKobo,
    inEscrow: formatNaira(inEscrowKobo),
    heldOrders: Number(held.rows[0].n),
  };
}

export async function merchantSummary(user: AuthUser) {
  if (user.role !== "MERCHANT") throw ApiError.forbidden();
  const w = await getOrCreateWallet(pool, user.id);
  const escrow = await pool.query(
    `SELECT COALESCE(SUM(amount_kobo),0) AS held
     FROM escrow_holds WHERE merchant_id = $1 AND status = 'HELD'`,
    [user.id],
  );
  const released = await pool.query(
    `SELECT COALESCE(SUM(amount_kobo),0) AS gross, COALESCE(SUM(commission_kobo),0) AS commission
     FROM escrow_holds WHERE merchant_id = $1 AND status = 'RELEASED'`,
    [user.id],
  );
  const paid = await pool.query(
    `SELECT COALESCE(SUM(amount_kobo),0) AS paid
     FROM payouts WHERE merchant_id = $1 AND status IN ('PAID','COMPLETED','SIMULATED','SIMULATED_PAID')`,
    [user.id],
  );
  const payouts = await myPayouts(user);
  const walletBalanceKobo = Number(w.balance_kobo);
  const inEscrowKobo = Number(escrow.rows[0].held);
  const releasedGrossKobo = Number(released.rows[0].gross);
  const commissionKobo = Number(released.rows[0].commission);
  const paidOutKobo = Number(paid.rows[0].paid);
  return {
    walletBalanceKobo,
    walletBalance: formatNaira(walletBalanceKobo),
    availableKobo: walletBalanceKobo,
    available: formatNaira(walletBalanceKobo),
    inEscrowKobo,
    inEscrow: formatNaira(inEscrowKobo),
    releasedGrossKobo,
    releasedGross: formatNaira(releasedGrossKobo),
    releasedKobo: releasedGrossKobo - commissionKobo,
    released: formatNaira(releasedGrossKobo - commissionKobo),
    commissionKobo,
    commission: formatNaira(commissionKobo),
    paidOutKobo,
    paidOut: formatNaira(paidOutKobo),
    payouts,
    currency: "NGN",
  };
}

export async function requestPayout(user: AuthUser, amountKobo: number) {
  if (user.role !== "MERCHANT") throw ApiError.forbidden();
  if (amountKobo < 100) throw ApiError.badRequest("VALIDATION_FAILED", "Minimum payout ₦1");
  return withTx(async (client) => {
    const w = await getOrCreateWallet(client, user.id);
    if (Number(w.balance_kobo) < amountKobo) throw ApiError.conflict("INSUFFICIENT_FUNDS", "Not enough balance");
    await client.query(`UPDATE wallets SET balance_kobo = balance_kobo - $2, updated_at = NOW() WHERE id = $1`, [
      w.id,
      amountKobo,
    ]);
    const id = uuid();
    await client.query(
      `INSERT INTO payouts (id, merchant_id, amount_kobo, status, bank_label, created_at, updated_at)
       VALUES ($1,$2,$3,'SIMULATED_PAID','Demo bank account',NOW(),NOW())`,
      [id, user.id, amountKobo],
    );
    await record(client, "PAYOUT", id, "Simulated payout", [
      { account: walletAccount(user.id), debit: amountKobo, credit: 0 },
      { account: EXTERNAL_ACCOUNT, debit: 0, credit: amountKobo },
    ]);
    return {
      id,
      amountKobo,
      amount: formatNaira(amountKobo),
      status: "SIMULATED_PAID",
      bankLabel: "Demo bank account",
      createdAt: new Date().toISOString(),
    };
  });
}

export async function myPayouts(user: AuthUser) {
  if (user.role !== "MERCHANT") throw ApiError.forbidden();
  const res = await pool.query(
    `SELECT * FROM payouts WHERE merchant_id = $1 ORDER BY created_at DESC LIMIT 50`,
    [user.id],
  );
  return res.rows.map((r) => ({
    id: r.id,
    amountKobo: Number(r.amount_kobo),
    amount: formatNaira(Number(r.amount_kobo)),
    status: r.status,
    bankLabel: r.bank_label,
    createdAt: r.created_at,
  }));
}
