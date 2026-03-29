CREATE TABLE businesses (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    instagram_account_id VARCHAR(255) UNIQUE,
    facebook_page_id VARCHAR(255) UNIQUE,
    access_token TEXT,
    token_expires_at TIMESTAMP,
    webhook_verify_token VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
);
