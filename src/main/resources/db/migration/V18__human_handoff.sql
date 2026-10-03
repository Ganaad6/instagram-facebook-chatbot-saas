-- Human handoff: a shop's staff can take over a conversation from the bot.
-- While bot_paused_until is in the future the bot stays silent for that customer.
ALTER TABLE customers ADD COLUMN bot_paused_until TIMESTAMP;
-- Set when the customer asked for a person and no staff reply has been sent yet
ALTER TABLE customers ADD COLUMN handoff_requested_at TIMESTAMP;
CREATE INDEX idx_customers_business_paused ON customers(business_id, bot_paused_until);

-- Who wrote each message: the customer, the bot, or a staff member (via the API or the
-- shop's Meta inbox)
ALTER TABLE messages ADD COLUMN sender_type VARCHAR(10);
UPDATE messages SET sender_type = CASE WHEN direction = 'INBOUND' THEN 'CUSTOMER' ELSE 'BOT' END;
ALTER TABLE messages ALTER COLUMN sender_type SET NOT NULL;
CREATE INDEX idx_messages_customer_id ON messages(customer_id, id);
