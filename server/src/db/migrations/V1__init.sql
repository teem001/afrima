CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    phone VARCHAR(32),
    role VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE stores (
    id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL REFERENCES users (id),
    name VARCHAR(255) NOT NULL,
    description VARCHAR(2000),
    address VARCHAR(512) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    open_at TIME NOT NULL,
    close_at TIME NOT NULL,
    delivery_radius_m INTEGER NOT NULL DEFAULT 5000,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_stores_merchant ON stores (merchant_id);
CREATE INDEX idx_stores_lat_lng ON stores (latitude, longitude);

CREATE TABLE products (
    id UUID PRIMARY KEY,
    sku VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(2000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE product_aliases (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    alias VARCHAR(255) NOT NULL,
    UNIQUE (product_id, alias)
);

CREATE INDEX idx_product_aliases_alias ON product_aliases (alias);

CREATE TABLE inventory_items (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL REFERENCES stores (id),
    product_id UUID NOT NULL REFERENCES products (id),
    quantity INTEGER NOT NULL CHECK (quantity >= 0),
    unit_price_kobo BIGINT NOT NULL CHECK (unit_price_kobo >= 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'NGN',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (store_id, product_id)
);

CREATE INDEX idx_inventory_store ON inventory_items (store_id);
CREATE INDEX idx_inventory_product ON inventory_items (product_id);

CREATE TABLE orders (
    id UUID PRIMARY KEY,
    consumer_id UUID NOT NULL REFERENCES users (id),
    store_id UUID NOT NULL REFERENCES stores (id),
    status VARCHAR(32) NOT NULL,
    total_kobo BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'NGN',
    ledger_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_orders_consumer ON orders (consumer_id);
CREATE INDEX idx_orders_store ON orders (store_id);

CREATE TABLE order_lines (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products (id),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price_kobo BIGINT NOT NULL
);
