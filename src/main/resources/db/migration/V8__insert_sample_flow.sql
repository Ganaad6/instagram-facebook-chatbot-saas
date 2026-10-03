-- Insert sample business
INSERT INTO businesses (name, email, instagram_account_id, facebook_page_id, created_at, updated_at, status)
VALUES ('Sample Business', 'sample@example.com', 'sample_instagram_id', 'sample_facebook_page_id', NOW(), NOW(), 'ACTIVE');

-- Insert sample flow
INSERT INTO chatbot_flows (business_id, name, is_active, created_at, updated_at)
VALUES (1, 'Lead Collection Flow', true, NOW(), NOW());

-- Insert flow steps without next_step_id first
INSERT INTO flow_steps (flow_id, step_order, step_key, message_template, field_name, validation_type, is_required, error_message)
VALUES (1, 1, 'greeting', 'Hello! Welcome to our service. What is your name?', 'customer_name', 'TEXT', true, 'Please enter a valid name.');

INSERT INTO flow_steps (flow_id, step_order, step_key, message_template, field_name, validation_type, is_required, error_message)
VALUES (1, 2, 'phone', 'Great, {customer_name}! What is your phone number? (10-15 digits)', 'phone_number', 'PHONE', true, 'Please enter a valid phone number (10-15 digits).');

INSERT INTO flow_steps (flow_id, step_order, step_key, message_template, field_name, validation_type, is_required, error_message)
VALUES (1, 3, 'address', 'Thank you! What is your address?', 'address', 'TEXT', true, 'Please enter a valid address.');

INSERT INTO flow_steps (flow_id, step_order, step_key, message_template, field_name, validation_type, is_required, error_message)
VALUES (1, 4, 'confirmation', 'Perfect! We have recorded your information. Is everything correct? (yes/no)', 'confirmation', 'TEXT', true, 'Please reply with yes or no.');

-- Update next_step_id references
UPDATE flow_steps SET next_step_id = (SELECT id FROM flow_steps WHERE step_key = 'phone' AND flow_id = 1)
WHERE step_key = 'greeting' AND flow_id = 1;

UPDATE flow_steps SET next_step_id = (SELECT id FROM flow_steps WHERE step_key = 'address' AND flow_id = 1)
WHERE step_key = 'phone' AND flow_id = 1;

UPDATE flow_steps SET next_step_id = (SELECT id FROM flow_steps WHERE step_key = 'confirmation' AND flow_id = 1)
WHERE step_key = 'address' AND flow_id = 1;
-- confirmation is the last step, next_step_id remains NULL
