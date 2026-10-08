-- The Facebook user (app-scoped id) who connected the shop's Page. Meta's deauthorize and
-- data-deletion callbacks identify the person only by this id.
ALTER TABLE businesses ADD COLUMN meta_user_id VARCHAR(64);
CREATE INDEX idx_businesses_meta_user_id ON businesses(meta_user_id);

-- Requests received through Meta's data-deletion callback. Only the confirmation code is kept,
-- not who asked, so the person can check the outcome at /data-deletion?code=...
CREATE TABLE data_deletion_requests (
    id                 BIGSERIAL PRIMARY KEY,
    confirmation_code  VARCHAR(32) NOT NULL UNIQUE,
    connections_removed INTEGER NOT NULL,
    completed_at       TIMESTAMP NOT NULL DEFAULT now()
);

-- A customer whose data the shop erased keeps their row (orders reference it) without their
-- Meta ids; if they write again they start over as a new customer.
ALTER TABLE customers ADD COLUMN erased_at TIMESTAMP;
ALTER TABLE customers DROP CONSTRAINT chk_customers_sender_id;
ALTER TABLE customers ADD CONSTRAINT chk_customers_sender_id
    CHECK (instagram_user_id IS NOT NULL OR facebook_user_id IS NOT NULL OR erased_at IS NOT NULL);

-- The retention job deletes chat messages by age
CREATE INDEX idx_messages_created_at ON messages(created_at);
