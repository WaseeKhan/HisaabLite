ALTER TABLE upgrade_requests
    ADD COLUMN receipt_original_filename VARCHAR(255) NULL,
    ADD COLUMN receipt_stored_filename VARCHAR(255) NULL,
    ADD COLUMN receipt_content_type VARCHAR(120) NULL,
    ADD COLUMN receipt_uploaded_at DATETIME NULL,
    ADD COLUMN gateway_transaction_id VARCHAR(255) NULL,
    ADD COLUMN payment_gateway VARCHAR(120) NULL,
    ADD COLUMN automated_payment_confirmed_at DATETIME NULL;

CREATE TABLE subscription_reminder_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    shop_id BIGINT NOT NULL,
    reminder_type VARCHAR(80) NOT NULL,
    target_date DATETIME NOT NULL,
    sent_at DATETIME NOT NULL,
    CONSTRAINT fk_subscription_reminder_shop
        FOREIGN KEY (shop_id) REFERENCES shops(id),
    CONSTRAINT uq_subscription_reminder_shop_type_target
        UNIQUE (shop_id, reminder_type, target_date)
);

CREATE TABLE subscription_ledger_entries (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    shop_id BIGINT NOT NULL,
    request_id BIGINT NULL,
    plan_type VARCHAR(30) NOT NULL,
    entry_type VARCHAR(50) NOT NULL,
    amount DOUBLE NULL,
    duration_in_days INT NULL,
    effective_from DATETIME NULL,
    effective_until DATETIME NULL,
    payment_reference VARCHAR(255) NULL,
    source_reference VARCHAR(255) NULL,
    note VARCHAR(1000) NULL,
    created_at DATETIME NOT NULL,
    created_by VARCHAR(255) NULL,
    CONSTRAINT fk_subscription_ledger_shop
        FOREIGN KEY (shop_id) REFERENCES shops(id),
    CONSTRAINT fk_subscription_ledger_request
        FOREIGN KEY (request_id) REFERENCES upgrade_requests(id)
);
