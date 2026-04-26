ALTER TABLE shops
    ADD COLUMN logo_original_filename VARCHAR(255) NULL,
    ADD COLUMN logo_stored_filename VARCHAR(255) NULL,
    ADD COLUMN logo_content_type VARCHAR(255) NULL,
    ADD COLUMN logo_uploaded_at TIMESTAMP NULL;
