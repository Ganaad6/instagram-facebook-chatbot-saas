-- When the reconcile last asked QPay about an unpaid invoice; it checks the least recently
-- checked first, so a large backlog can't starve newer orders
ALTER TABLE orders ADD COLUMN payment_checked_at TIMESTAMP;
