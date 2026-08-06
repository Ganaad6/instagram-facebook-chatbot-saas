CREATE TABLE orders (
    id            BIGSERIAL PRIMARY KEY,
    business_id   BIGINT NOT NULL REFERENCES businesses(id),
    customer_id   BIGINT NOT NULL REFERENCES customers(id),
    product_id    BIGINT NOT NULL REFERENCES products(id),
    customer_name VARCHAR(255),
    phone         VARCHAR(50),
    address       TEXT,
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    platform      VARCHAR(20) NOT NULL,
    notes         TEXT,
    created_at    TIMESTAMP NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_orders_business_id ON orders(business_id);
CREATE INDEX idx_orders_customer_id ON orders(customer_id);
CREATE INDEX idx_orders_status      ON orders(status);
