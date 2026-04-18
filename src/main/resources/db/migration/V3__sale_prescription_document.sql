ALTER TABLE sale
    ADD COLUMN prescription_document_path VARCHAR(500) NULL,
    ADD COLUMN prescription_document_name VARCHAR(255) NULL,
    ADD COLUMN prescription_document_content_type VARCHAR(120) NULL;
