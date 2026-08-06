CREATE TABLE products (
    id          BIGSERIAL PRIMARY KEY,
    business_id BIGINT NOT NULL REFERENCES businesses(id),
    category_id BIGINT NOT NULL REFERENCES categories(id),
    name        VARCHAR(255) NOT NULL,
    price       NUMERIC(12,2) NOT NULL,
    description TEXT,
    is_active   BOOLEAN NOT NULL DEFAULT true,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_products_business_id  ON products(business_id);
CREATE INDEX idx_products_category_id ON products(category_id);
