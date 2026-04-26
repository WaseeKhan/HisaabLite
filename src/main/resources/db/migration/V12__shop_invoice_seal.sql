ALTER TABLE shops
    ADD COLUMN seal_original_filename VARCHAR(255) NULL,
    ADD COLUMN seal_stored_filename VARCHAR(255) NULL,
    ADD COLUMN seal_content_type VARCHAR(255) NULL,
    ADD COLUMN seal_uploaded_at TIMESTAMP NULL;
