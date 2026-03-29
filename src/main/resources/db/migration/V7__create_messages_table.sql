CREATE TABLE messages (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT REFERENCES conversations(id),
    customer_id BIGINT NOT NULL REFERENCES customers(id),
    business_id BIGINT NOT NULL REFERENCES businesses(id),
    message_id VARCHAR(255) NOT NULL,
    direction VARCHAR(10) NOT NULL,
    content TEXT NOT NULL,
    sent_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_messages_conversation_id ON messages(conversation_id);
CREATE INDEX idx_messages_business_id ON messages(business_id);
