CREATE TABLE conversation_data (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL REFERENCES conversations(id),
    field_name VARCHAR(100) NOT NULL,
    field_value TEXT NOT NULL,
    collected_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_conversation_data_conversation_id ON conversation_data(conversation_id);
