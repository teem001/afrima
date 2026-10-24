-- Add media columns then FKs (H2 rejected multi-column ADD with inline REFERENCES).
CREATE TABLE media_assets (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    byte_size INTEGER NOT NULL,
    data BYTEA NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

ALTER TABLE media_assets
    ADD CONSTRAINT fk_media_assets_owner FOREIGN KEY (owner_id) REFERENCES users (id);

CREATE INDEX idx_media_assets_owner ON media_assets (owner_id);

ALTER TABLE stores ADD COLUMN image_media_id UUID;
ALTER TABLE stores ADD COLUMN phone VARCHAR(32);
ALTER TABLE stores ADD COLUMN category VARCHAR(128);
ALTER TABLE stores
    ADD CONSTRAINT fk_stores_image_media FOREIGN KEY (image_media_id) REFERENCES media_assets (id);

ALTER TABLE products ADD COLUMN image_media_id UUID;
ALTER TABLE products
    ADD CONSTRAINT fk_products_image_media FOREIGN KEY (image_media_id) REFERENCES media_assets (id);
