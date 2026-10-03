-- Dashboard logins: a shop's owner and staff sign in with email + password. An email belongs
-- to exactly one shop. The shop's API key stays for integrations.
CREATE TABLE staff_users (
    id               BIGSERIAL PRIMARY KEY,
    business_id      BIGINT NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    email            VARCHAR(255) NOT NULL,
    name             VARCHAR(255),
    -- NULL until the invite is accepted
    password_hash    VARCHAR(100),
    role             VARCHAR(10) NOT NULL,
    active           BOOLEAN NOT NULL DEFAULT TRUE,
    -- Bumped on password change/reset or deactivation, signing out every existing session
    session_version  INTEGER NOT NULL DEFAULT 0,
    failed_logins    INTEGER NOT NULL DEFAULT 0,
    locked_until     TIMESTAMP,
    last_login_at    TIMESTAMP,
    created_at       TIMESTAMP NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT chk_staff_users_role CHECK (role IN ('OWNER', 'STAFF'))
);
CREATE UNIQUE INDEX uq_staff_users_email ON staff_users (lower(email));
CREATE INDEX idx_staff_users_business_id ON staff_users (business_id);

-- One-time links for accepting an invite or resetting a password. Only a SHA-256 hash of the
-- token is stored, like API keys.
CREATE TABLE staff_tokens (
    id             BIGSERIAL PRIMARY KEY,
    staff_user_id  BIGINT NOT NULL REFERENCES staff_users(id) ON DELETE CASCADE,
    token_hash     VARCHAR(64) NOT NULL UNIQUE,
    purpose        VARCHAR(10) NOT NULL,
    expires_at     TIMESTAMP NOT NULL,
    used_at        TIMESTAMP,
    created_at     TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT chk_staff_tokens_purpose CHECK (purpose IN ('INVITE', 'RESET'))
);
CREATE INDEX idx_staff_tokens_user ON staff_tokens (staff_user_id);
