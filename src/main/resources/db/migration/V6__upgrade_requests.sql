CREATE TABLE IF NOT EXISTS upgrade_requests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    shop_id BIGINT NOT NULL,
    requested_by BIGINT NOT NULL,
    current_plan ENUM('FREE', 'BASIC', 'PREMIUM', 'ENTERPRISE') NOT NULL,
    requested_plan ENUM('FREE', 'BASIC', 'PREMIUM', 'ENTERPRISE') NOT NULL,
    status ENUM('REQUESTED', 'CONTACTED', 'PAYMENT_RECEIVED', 'ACTIVATED', 'REJECTED', 'CANCELLED') NOT NULL,
    payment_preference VARCHAR(255),
    payment_reference VARCHAR(255),
    note VARCHAR(1000),
    admin_note VARCHAR(1000),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6),
    activated_at DATETIME(6),
    activated_by BIGINT,
    PRIMARY KEY (id),
    CONSTRAINT fk_upgrade_request_shop FOREIGN KEY (shop_id) REFERENCES shops (id),
    CONSTRAINT fk_upgrade_request_requested_by FOREIGN KEY (requested_by) REFERENCES users (id),
    CONSTRAINT fk_upgrade_request_activated_by FOREIGN KEY (activated_by) REFERENCES users (id)
);

CREATE INDEX idx_upgrade_requests_shop_status ON upgrade_requests (shop_id, status);
CREATE INDEX idx_upgrade_requests_status_created ON upgrade_requests (status, created_at);
