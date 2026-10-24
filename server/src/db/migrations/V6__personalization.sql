CREATE TABLE product_categories (
    id UUID PRIMARY KEY,
    slug VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    parent_id UUID REFERENCES product_categories (id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

INSERT INTO product_categories (id, slug, name, parent_id, created_at, updated_at) VALUES
  ('a1000000-0000-4000-8000-000000000001', 'staples', 'Staples', NULL, NOW(), NOW()),
  ('a1000000-0000-4000-8000-000000000002', 'beverages', 'Beverages', NULL, NOW(), NOW()),
  ('a1000000-0000-4000-8000-000000000003', 'household', 'Household', NULL, NOW(), NOW()),
  ('a1000000-0000-4000-8000-000000000004', 'personal_care', 'Personal care', NULL, NOW(), NOW()),
  ('a1000000-0000-4000-8000-000000000005', 'snacks', 'Snacks', NULL, NOW(), NOW()),
  ('a1000000-0000-4000-8000-000000000006', 'fresh', 'Fresh food', NULL, NOW(), NOW()),
  ('a1000000-0000-4000-8000-000000000007', 'electronics', 'Electronics', NULL, NOW(), NOW()),
  ('a1000000-0000-4000-8000-000000000008', 'other', 'Other', NULL, NOW(), NOW());

ALTER TABLE products ADD COLUMN category_id UUID REFERENCES product_categories (id);
UPDATE products SET category_id = 'a1000000-0000-4000-8000-000000000008' WHERE category_id IS NULL;

CREATE TABLE user_behavior_events (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type VARCHAR(32) NOT NULL,
    query VARCHAR(255),
    product_id UUID REFERENCES products (id) ON DELETE SET NULL,
    store_id UUID REFERENCES stores (id) ON DELETE SET NULL,
    category_id UUID REFERENCES product_categories (id) ON DELETE SET NULL,
    unit_price_kobo BIGINT,
    quantity INTEGER,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    payload TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_behavior_user_created ON user_behavior_events (user_id, created_at DESC);
CREATE INDEX idx_behavior_user_type ON user_behavior_events (user_id, type);

CREATE TABLE shopper_preferences (
    user_id UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    top_categories TEXT NOT NULL DEFAULT '[]',
    top_products TEXT NOT NULL DEFAULT '[]',
    price_min_kobo BIGINT,
    price_p50_kobo BIGINT,
    price_max_kobo BIGINT,
    preferred_radius_m INTEGER,
    preferred_lat DOUBLE PRECISION,
    preferred_lng DOUBLE PRECISION,
    order_count INTEGER NOT NULL DEFAULT 0,
    avg_order_kobo BIGINT,
    order_frequency_days DOUBLE PRECISION,
    discount_sensitivity DOUBLE PRECISION NOT NULL DEFAULT 0.5,
    last_ai_review TEXT,
    last_ai_review_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE notification_subscriptions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    category_id UUID REFERENCES product_categories (id) ON DELETE CASCADE,
    query VARCHAR(255),
    max_price_kobo BIGINT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_notif_subs_user ON notification_subscriptions (user_id, active);

CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type VARCHAR(32) NOT NULL,
    title VARCHAR(255) NOT NULL,
    body VARCHAR(1000) NOT NULL,
    href VARCHAR(512),
    payload TEXT,
    read_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_notifications_user ON notifications (user_id, created_at DESC);
CREATE INDEX idx_notifications_unread ON notifications (user_id, read_at);
