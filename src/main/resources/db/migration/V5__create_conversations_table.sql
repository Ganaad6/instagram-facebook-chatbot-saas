CREATE TABLE conversations (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers(id),
    business_id BIGINT NOT NULL REFERENCES businesses(id),
    flow_id BIGINT NOT NULL REFERENCES chatbot_flows(id),
    current_step_id BIGINT REFERENCES flow_steps(id),
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_conversations_customer_id ON conversations(customer_id);
CREATE INDEX idx_conversations_business_id ON conversations(business_id);
