ALTER TABLE sale
    ADD COLUMN doctor_name VARCHAR(255) NULL,
    ADD COLUMN prescription_date DATE NULL,
    ADD COLUMN prescription_reference VARCHAR(255) NULL,
    ADD COLUMN prescription_required BIT(1) NOT NULL DEFAULT b'0',
    ADD COLUMN prescription_verified BIT(1) NOT NULL DEFAULT b'0';
