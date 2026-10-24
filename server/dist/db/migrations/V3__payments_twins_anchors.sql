CREATE TABLE wallets (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL UNIQUE REFERENCES users (id),
    balance_kobo BIGINT NOT NULL DEFAULT 0,
    currency VARCHAR(8) NOT NULL DEFAULT 'NGN',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE ledger_transactions (
    id UUID PRIMARY KEY,
    type VARCHAR(32) NOT NULL,
    reference_id UUID,
    description VARCHAR(512),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_ledger_tx_reference ON ledger_transactions (reference_id);

CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL REFERENCES ledger_transactions (id) ON DELETE CASCADE,
    account VARCHAR(128) NOT NULL,
    debit_kobo BIGINT NOT NULL DEFAULT 0,
    credit_kobo BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_ledger_entries_account ON ledger_entries (account);
CREATE INDEX idx_ledger_entries_tx ON ledger_entries (transaction_id);

CREATE TABLE escrow_holds (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE REFERENCES orders (id),
    consumer_id UUID NOT NULL REFERENCES users (id),
    merchant_id UUID NOT NULL REFERENCES users (id),
    store_id UUID NOT NULL REFERENCES stores (id),
    amount_kobo BIGINT NOT NULL,
    commission_kobo BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_escrow_merchant_status ON escrow_holds (merchant_id, status);
CREATE INDEX idx_escrow_consumer_status ON escrow_holds (consumer_id, status);

CREATE TABLE payouts (
    id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL REFERENCES users (id),
    amount_kobo BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    bank_label VARCHAR(128) NOT NULL DEFAULT 'Demo bank account',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_payouts_merchant ON payouts (merchant_id, created_at);

CREATE TABLE anchor_batches (
    id UUID PRIMARY KEY,
    merkle_root VARCHAR(64) NOT NULL,
    tx_reference VARCHAR(128) NOT NULL,
    order_count INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'SIMULATED',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE anchor_items (
    id UUID PRIMARY KEY,
    batch_id UUID NOT NULL REFERENCES anchor_batches (id) ON DELETE CASCADE,
    order_id UUID NOT NULL UNIQUE REFERENCES orders (id),
    ledger_hash VARCHAR(64) NOT NULL,
    leaf_index INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_anchor_items_batch ON anchor_items (batch_id, leaf_index);
