ALTER TABLE businesses ADD COLUMN api_key_hash VARCHAR(64) UNIQUE;
ALTER TABLE businesses ADD COLUMN api_key_created_at TIMESTAMP;
