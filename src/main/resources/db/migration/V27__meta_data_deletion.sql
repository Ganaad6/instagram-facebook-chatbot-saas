-- Meta's deauthorize and data-deletion callbacks identify the Facebook user who connected a
-- shop by their app-scoped user id; remember it at connect time so those callbacks can find the
-- shop. data_deletion_requests backs the status page Meta links the person to.
ALTER TABLE businesses ADD COLUMN meta_user_id VARCHAR(64);
CREATE INDEX idx_businesses_meta_user_id ON businesses (meta_user_id);

CREATE TABLE data_deletion_requests (
    id                BIGSERIAL PRIMARY KEY,
    confirmation_code VARCHAR(32) NOT NULL UNIQUE,
    shops_affected    INTEGER     NOT NULL,
    requested_at      TIMESTAMP   NOT NULL,
    completed_at      TIMESTAMP
);
