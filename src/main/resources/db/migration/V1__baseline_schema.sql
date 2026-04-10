CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(255) NOT NULL,
    user_role VARCHAR(255) NOT NULL,
    shop_id BIGINT NULL,
    shop_name VARCHAR(255) NULL,
    action VARCHAR(255) NOT NULL,
    details VARCHAR(1000) NULL,
    entity_type VARCHAR(255) NULL,
    entity_id BIGINT NULL,
    ip_address VARCHAR(255) NULL,
    status VARCHAR(255) NULL,
    timestamp DATETIME(6) NOT NULL,
    old_value VARCHAR(2000) NULL,
    new_value VARCHAR(2000) NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS shops (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    pan_number VARCHAR(255) NOT NULL,
    gst_number VARCHAR(255) NULL,
    address VARCHAR(255) NULL,
    city VARCHAR(255) NULL,
    state VARCHAR(255) NULL,
    pincode VARCHAR(255) NULL,
    staff_limit INT NULL,
    plan_type ENUM('BASIC','ENTERPRISE','FREE','PREMIUM') NULL,
    upi_id VARCHAR(255) NULL,
    active BIT(1) NOT NULL,
    created_at DATETIME(6) NULL,
    whatsapp_number VARCHAR(255) NULL,
    whatsapp_instance_name VARCHAR(255) NULL,
    whatsapp_qr_code VARCHAR(100000) NULL,
    whatsapp_connected BIT(1) NULL,
    whatsapp_connected_at DATETIME(6) NULL,
    subscription_start_date DATETIME(6) NULL,
    subscription_end_date DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_shops_pan_number (pan_number)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    username VARCHAR(255) NOT NULL,
    phone VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    role ENUM('ADMIN','CASHIER','MANAGER','OWNER') NOT NULL,
    approved BIT(1) NOT NULL,
    active BIT(1) NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    current_plan ENUM('BASIC','ENTERPRISE','FREE','PREMIUM') NULL,
    subscription_start_date DATETIME(6) NULL,
    subscription_end_date DATETIME(6) NULL,
    approval_date DATETIME(6) NULL,
    shop_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username),
    UNIQUE KEY uk_users_phone (phone),
    CONSTRAINT fk_users_shop FOREIGN KEY (shop_id) REFERENCES shops (id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS email_verification_token (
    id BIGINT NOT NULL AUTO_INCREMENT,
    token VARCHAR(255) NOT NULL,
    expiry_date DATETIME(6) NOT NULL,
    user_id BIGINT NOT NULL,
    token_type ENUM('EMAIL_VERIFICATION','PASSWORD_RESET') NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_email_verification_token_token (token),
    UNIQUE KEY uk_email_verification_token_user (user_id),
    CONSTRAINT fk_email_verification_token_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS password_reset_token (
    id BIGINT NOT NULL AUTO_INCREMENT,
    token VARCHAR(255) NULL,
    expiry_date DATETIME(6) NULL,
    user_id BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_password_reset_token_user (user_id),
    CONSTRAINT fk_password_reset_token_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS product (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version BIGINT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(255) NULL,
    barcode VARCHAR(255) NULL,
    generic_name VARCHAR(255) NULL,
    manufacturer VARCHAR(255) NULL,
    pack_size VARCHAR(255) NULL,
    price DECIMAL(38,2) NOT NULL,
    mrp DECIMAL(38,2) NULL,
    purchase_price DECIMAL(38,2) NULL,
    stock_quantity INT NOT NULL,
    min_stock INT NOT NULL,
    gst_percent INT NULL,
    prescription_required BIT(1) NOT NULL,
    shop_id BIGINT NOT NULL,
    active BIT(1) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_product_shop FOREIGN KEY (shop_id) REFERENCES shops (id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS supplier (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NULL,
    phone VARCHAR(255) NULL,
    contact_person VARCHAR(255) NULL,
    gst_number VARCHAR(255) NULL,
    address VARCHAR(255) NULL,
    notes VARCHAR(255) NULL,
    active BIT(1) NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    shop_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    KEY idx_supplier_shop_name (shop_id, name),
    CONSTRAINT fk_supplier_shop FOREIGN KEY (shop_id) REFERENCES shops (id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS purchase_entry (
    id BIGINT NOT NULL AUTO_INCREMENT,
    purchase_date DATE NULL,
    created_at DATETIME(6) NULL,
    supplier_name VARCHAR(255) NULL,
    supplier_invoice_number VARCHAR(255) NULL,
    notes VARCHAR(255) NULL,
    total_amount DECIMAL(38,2) NULL,
    supplier_id BIGINT NULL,
    shop_id BIGINT NOT NULL,
    created_by BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_purchase_entry_supplier FOREIGN KEY (supplier_id) REFERENCES supplier (id),
    CONSTRAINT fk_purchase_entry_shop FOREIGN KEY (shop_id) REFERENCES shops (id),
    CONSTRAINT fk_purchase_entry_created_by FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS purchase_batch (
    id BIGINT NOT NULL AUTO_INCREMENT,
    batch_number VARCHAR(255) NULL,
    expiry_date DATE NULL,
    received_quantity INT NULL,
    available_quantity INT NULL,
    purchase_price DECIMAL(38,2) NULL,
    mrp DECIMAL(38,2) NULL,
    sale_price DECIMAL(38,2) NULL,
    created_at DATETIME(6) NULL,
    active BIT(1) NOT NULL,
    purchase_entry_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    shop_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    KEY idx_purchase_batch_shop_product (shop_id, product_id),
    KEY idx_purchase_batch_expiry (expiry_date),
    CONSTRAINT fk_purchase_batch_purchase_entry FOREIGN KEY (purchase_entry_id) REFERENCES purchase_entry (id),
    CONSTRAINT fk_purchase_batch_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT fk_purchase_batch_shop FOREIGN KEY (shop_id) REFERENCES shops (id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS purchase_return (
    id BIGINT NOT NULL AUTO_INCREMENT,
    return_date DATE NULL,
    created_at DATETIME(6) NULL,
    supplier_name VARCHAR(255) NULL,
    reference_invoice_number VARCHAR(255) NULL,
    notes VARCHAR(255) NULL,
    total_amount DECIMAL(38,2) NULL,
    supplier_id BIGINT NULL,
    shop_id BIGINT NOT NULL,
    created_by BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_purchase_return_supplier FOREIGN KEY (supplier_id) REFERENCES supplier (id),
    CONSTRAINT fk_purchase_return_shop FOREIGN KEY (shop_id) REFERENCES shops (id),
    CONSTRAINT fk_purchase_return_created_by FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS purchase_return_line (
    id BIGINT NOT NULL AUTO_INCREMENT,
    line_amount DECIMAL(38,2) NULL,
    quantity INT NULL,
    reason VARCHAR(255) NULL,
    product_id BIGINT NOT NULL,
    purchase_batch_id BIGINT NOT NULL,
    purchase_return_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_purchase_return_line_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT fk_purchase_return_line_batch FOREIGN KEY (purchase_batch_id) REFERENCES purchase_batch (id),
    CONSTRAINT fk_purchase_return_line_return FOREIGN KEY (purchase_return_id) REFERENCES purchase_return (id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS sale (
    id BIGINT NOT NULL AUTO_INCREMENT,
    sale_date DATETIME(6) NULL,
    total_amount DECIMAL(38,2) NULL,
    total_gst_amount DECIMAL(38,2) NULL,
    taxable_amount DECIMAL(38,2) NULL,
    customer_name VARCHAR(255) NULL,
    customer_phone VARCHAR(255) NULL,
    payment_mode VARCHAR(255) NULL,
    amount_received DOUBLE NULL,
    change_returned DOUBLE NULL,
    discount_amount DECIMAL(38,2) NULL,
    discount_percent DECIMAL(38,2) NULL,
    status ENUM('CANCELLED','COMPLETED') NULL,
    shop_id BIGINT NULL,
    created_by BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_sale_shop FOREIGN KEY (shop_id) REFERENCES shops (id),
    CONSTRAINT fk_sale_created_by FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS sale_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    quantity INT NULL,
    price_at_sale DECIMAL(38,2) NULL,
    subtotal DECIMAL(38,2) NULL,
    gst_percent INT NULL,
    gst_amount DECIMAL(38,2) NULL,
    total_with_gst DECIMAL(38,2) NULL,
    product_id BIGINT NOT NULL,
    sale_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_sale_item_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT fk_sale_item_sale FOREIGN KEY (sale_id) REFERENCES sale (id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS sale_item_batch_allocation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    quantity INT NULL,
    purchase_batch_id BIGINT NOT NULL,
    sale_item_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_sale_item_batch_allocation_batch FOREIGN KEY (purchase_batch_id) REFERENCES purchase_batch (id),
    CONSTRAINT fk_sale_item_batch_allocation_sale_item FOREIGN KEY (sale_item_id) REFERENCES sale_item (id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS stock_adjustment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    adjustment_date DATE NULL,
    created_at DATETIME(6) NULL,
    notes VARCHAR(255) NULL,
    reason VARCHAR(255) NULL,
    quantity_delta INT NULL,
    previous_batch_quantity INT NULL,
    new_batch_quantity INT NULL,
    previous_product_stock INT NULL,
    new_product_stock INT NULL,
    created_by BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    purchase_batch_id BIGINT NULL,
    shop_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_stock_adjustment_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_stock_adjustment_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT fk_stock_adjustment_purchase_batch FOREIGN KEY (purchase_batch_id) REFERENCES purchase_batch (id),
    CONSTRAINT fk_stock_adjustment_shop FOREIGN KEY (shop_id) REFERENCES shops (id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS subscription_plans (
    id BIGINT NOT NULL AUTO_INCREMENT,
    plan_name VARCHAR(255) NOT NULL,
    price DOUBLE NOT NULL,
    duration_in_days INT NULL,
    description VARCHAR(255) NULL,
    features VARCHAR(255) NULL,
    max_users INT NULL,
    max_products INT NULL,
    active BIT(1) NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_subscription_plans_plan_name (plan_name)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS support_tickets (
    id BIGINT NOT NULL AUTO_INCREMENT,
    ticket_number VARCHAR(255) NOT NULL,
    user_id BIGINT NOT NULL,
    shop_id BIGINT NOT NULL,
    subject VARCHAR(255) NOT NULL,
    message VARCHAR(5000) NOT NULL,
    status ENUM('CLOSED','IN_PROGRESS','OPEN','RESOLVED','WAITING_CUSTOMER') NOT NULL,
    priority ENUM('HIGH','LOW','MEDIUM','URGENT') NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    resolved_at DATETIME(6) NULL,
    attachment_url VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_support_tickets_ticket_number (ticket_number),
    CONSTRAINT fk_support_tickets_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_support_tickets_shop FOREIGN KEY (shop_id) REFERENCES shops (id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS ticket_replies (
    id BIGINT NOT NULL AUTO_INCREMENT,
    ticket_id BIGINT NOT NULL,
    user_id BIGINT NULL,
    message VARCHAR(5000) NOT NULL,
    is_admin_reply BIT(1) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    attachment_url VARCHAR(500) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ticket_replies_ticket FOREIGN KEY (ticket_id) REFERENCES support_tickets (id),
    CONSTRAINT fk_ticket_replies_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;
