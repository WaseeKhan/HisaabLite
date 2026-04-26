ALTER TABLE upgrade_requests
    ADD COLUMN status_updated_at DATETIME NULL,
    ADD COLUMN contacted_at DATETIME NULL,
    ADD COLUMN payment_received_at DATETIME NULL,
    ADD COLUMN rejected_at DATETIME NULL,
    ADD COLUMN cancelled_at DATETIME NULL;
