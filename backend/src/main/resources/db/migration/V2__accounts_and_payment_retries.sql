CREATE TABLE app_users (
 username VARCHAR(60) PRIMARY KEY,
 password_hash VARCHAR(100) NOT NULL,
 role VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN','SALES','PRODUCTION','ACCOUNTS')),
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE payments ADD COLUMN request_key VARCHAR(36);
ALTER TABLE payments ADD COLUMN reference VARCHAR(100);
ALTER TABLE payments ADD COLUMN recorded_by VARCHAR(60);
ALTER TABLE payments ADD CONSTRAINT uq_payment_request UNIQUE(request_key);
ALTER TABLE payments ADD CONSTRAINT uq_payment_reference UNIQUE(order_id, reference);
ALTER TABLE audit_events ADD COLUMN actor VARCHAR(60);
