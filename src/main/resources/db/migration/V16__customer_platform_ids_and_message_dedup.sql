-- Store each sender under the ID column for its platform (Facebook PSID vs Instagram IGSID).
-- Previously every sender was stored as instagram_user_id, including Facebook ones.
ALTER TABLE customers ALTER COLUMN instagram_user_id DROP NOT NULL;
UPDATE customers
SET facebook_user_id = instagram_user_id,
    instagram_user_id = NULL
WHERE facebook_user_id IS NULL
  AND id IN (SELECT customer_id FROM conversations WHERE platform = 'FACEBOOK');
CREATE UNIQUE INDEX uq_customers_business_facebook_user ON customers(business_id, facebook_user_id);
ALTER TABLE customers ADD CONSTRAINT chk_customers_sender_id
    CHECK (instagram_user_id IS NOT NULL OR facebook_user_id IS NOT NULL);

-- Meta redelivers webhooks it thinks failed; inbound messages are stored under Meta's own
-- message id (mid) so a redelivery is detected and skipped instead of processed twice.
CREATE UNIQUE INDEX uq_messages_business_message_id ON messages(business_id, message_id);

CREATE INDEX idx_conversations_customer_status ON conversations(customer_id, status);
