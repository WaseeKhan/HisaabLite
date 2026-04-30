ALTER TABLE shops
    ADD COLUMN admin_attention_level VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
    ADD COLUMN admin_internal_note TEXT NULL,
    ADD COLUMN admin_last_reviewed_at DATETIME NULL,
    ADD COLUMN admin_last_reviewed_by VARCHAR(255) NULL;
