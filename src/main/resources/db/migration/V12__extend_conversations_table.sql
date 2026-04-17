-- Make flow_id nullable to support state-machine driven conversations (no flow needed)
ALTER TABLE conversations ALTER COLUMN flow_id DROP NOT NULL;

-- Add product-order chatbot state machine columns
ALTER TABLE conversations ADD COLUMN state                VARCHAR(30) NOT NULL DEFAULT 'IDLE';
ALTER TABLE conversations ADD COLUMN selected_category_id BIGINT REFERENCES categories(id);
ALTER TABLE conversations ADD COLUMN selected_product_id  BIGINT REFERENCES products(id);
ALTER TABLE conversations ADD COLUMN order_id             BIGINT REFERENCES orders(id);
ALTER TABLE conversations ADD COLUMN collected_name       VARCHAR(255);
ALTER TABLE conversations ADD COLUMN collected_phone      VARCHAR(50);
ALTER TABLE conversations ADD COLUMN collected_address    TEXT;
ALTER TABLE conversations ADD COLUMN platform             VARCHAR(20);
