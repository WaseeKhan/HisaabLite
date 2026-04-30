ALTER TABLE support_tickets
    ADD COLUMN root_cause VARCHAR(64) NULL,
    ADD COLUMN internal_note TEXT NULL,
    ADD COLUMN assigned_admin_username VARCHAR(255) NULL,
    ADD COLUMN due_at DATETIME NULL,
    ADD COLUMN related_subscription_issue BIT(1) NOT NULL DEFAULT b'0',
    ADD COLUMN related_whatsapp_issue BIT(1) NOT NULL DEFAULT b'0';

ALTER TABLE shops
    ADD COLUMN whatsapp_admin_disabled BIT(1) NOT NULL DEFAULT b'0';
