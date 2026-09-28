-- Orders keep their own copy of what was bought and at what price, so later edits to the
-- product (rename, price change) don't rewrite order history, and support a quantity.
ALTER TABLE orders ADD COLUMN product_name VARCHAR(255);
ALTER TABLE orders ADD COLUMN unit_price NUMERIC(12,2);
ALTER TABLE orders ADD COLUMN quantity INTEGER DEFAULT 1 NOT NULL;
ALTER TABLE orders ADD COLUMN total_amount NUMERIC(12,2);

-- Backfill existing orders from the product's current values (the best information available)
UPDATE orders SET
    product_name = (SELECT p.name FROM products p WHERE p.id = orders.product_id),
    unit_price   = (SELECT p.price FROM products p WHERE p.id = orders.product_id);
UPDATE orders SET total_amount = unit_price * quantity;

ALTER TABLE orders ALTER COLUMN product_name SET NOT NULL;
ALTER TABLE orders ALTER COLUMN unit_price SET NOT NULL;
ALTER TABLE orders ALTER COLUMN total_amount SET NOT NULL;
ALTER TABLE orders ADD CONSTRAINT chk_orders_quantity_positive CHECK (quantity > 0);

-- Quantity chosen in the chatbot before the order is placed
ALTER TABLE conversations ADD COLUMN selected_quantity INTEGER;
