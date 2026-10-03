CREATE TABLE flow_steps (
    id BIGSERIAL PRIMARY KEY,
    flow_id BIGINT NOT NULL REFERENCES chatbot_flows(id),
    step_order INTEGER NOT NULL,
    step_key VARCHAR(100) NOT NULL,
    message_template TEXT NOT NULL,
    field_name VARCHAR(100),
    validation_type VARCHAR(50),
    validation_regex VARCHAR(500),
    is_required BOOLEAN NOT NULL DEFAULT true,
    next_step_id BIGINT REFERENCES flow_steps(id),
    error_message TEXT
);
CREATE INDEX idx_flow_steps_flow_id ON flow_steps(flow_id);
