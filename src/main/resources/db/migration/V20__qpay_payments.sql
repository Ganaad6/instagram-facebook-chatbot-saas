-- QPay payments: each shop connects its own QPay merchant account, so customers pay the shop
-- directly. The password is stored AES-GCM encrypted, like the Meta Page token.
ALTER TABLE businesses ADD COLUMN qpay_username VARCHAR(255);
ALTER TABLE businesses ADD COLUMN qpay_password TEXT;
ALTER TABLE businesses ADD COLUMN qpay_invoice_code VARCHAR(255);

-- Payment state is tracked separately from the fulfilment status (PENDING/CONFIRMED/...).
-- NOT_REQUESTED: no online payment (shop has no QPay, or the invoice could not be created)
ALTER TABLE orders ADD COLUMN payment_status VARCHAR(20) DEFAULT 'NOT_REQUESTED' NOT NULL;
ALTER TABLE orders ADD COLUMN qpay_invoice_id VARCHAR(64);
ALTER TABLE orders ADD COLUMN payment_url TEXT;
ALTER TABLE orders ADD COLUMN qpay_payment_id VARCHAR(64);
ALTER TABLE orders ADD COLUMN paid_at TIMESTAMP;
ALTER TABLE orders ADD CONSTRAINT uq_orders_qpay_invoice_id UNIQUE (qpay_invoice_id);
-- The reconciler looks for recent unpaid invoices
CREATE INDEX idx_orders_payment_pending ON orders(created_at) WHERE payment_status = 'PENDING';
