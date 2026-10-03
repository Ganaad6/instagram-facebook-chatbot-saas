CREATE TABLE categories (
    id          BIGSERIAL PRIMARY KEY,
    business_id BIGINT NOT NULL REFERENCES businesses(id),
    name        VARCHAR(255) NOT NULL,
    sort_order  INTEGER NOT NULL DEFAULT 0,
    is_active   BOOLEAN NOT NULL DEFAULT true,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_categories_business_id ON categories(business_id);
