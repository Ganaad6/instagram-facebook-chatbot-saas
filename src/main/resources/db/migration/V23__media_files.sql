-- Product photos uploaded from the dashboard, stored in the database so backups (pg_dump)
-- cover them and no separate file store is needed. Served publicly at /media/{id} - Meta
-- fetches them from there when the bot sends a product photo. products.image_file_id holds
-- the id (older photos uploaded through Directus keep their Directus file id).
CREATE TABLE media_files (
    id            UUID PRIMARY KEY,
    business_id   BIGINT NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    content_type  VARCHAR(50) NOT NULL,
    size_bytes    INTEGER NOT NULL,
    data          BYTEA NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_media_files_business_id ON media_files (business_id);
