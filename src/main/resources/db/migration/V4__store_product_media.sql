CREATE TABLE media_assets (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users (id),
    content_type VARCHAR(128) NOT NULL,
    byte_size INTEGER NOT NULL,
    data BYTEA NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_media_assets_owner ON media_assets (owner_id);

ALTER TABLE stores
    ADD COLUMN image_media_id UUID REFERENCES media_assets (id),
    ADD COLUMN phone VARCHAR(32),
    ADD COLUMN category VARCHAR(128);

ALTER TABLE products
    ADD COLUMN image_media_id UUID REFERENCES media_assets (id);
