CREATE TABLE customers (
    id BIGSERIAL PRIMARY KEY,
    business_id BIGINT NOT NULL REFERENCES businesses(id),
    instagram_user_id VARCHAR(255) NOT NULL,
    facebook_user_id VARCHAR(255),
    first_interaction_at TIMESTAMP NOT NULL,
    last_interaction_at TIMESTAMP NOT NULL,
    UNIQUE (business_id, instagram_user_id)
);
CREATE INDEX idx_customers_business_id ON customers(business_id);
