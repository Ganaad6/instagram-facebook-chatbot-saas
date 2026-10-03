-- Remove the legacy flow-step chatbot's tables (its code was removed earlier). This
-- permanently deletes any answers collected by old flows (conversation_data).
ALTER TABLE conversations DROP COLUMN current_step_id;
ALTER TABLE conversations DROP COLUMN flow_id;
DROP TABLE conversation_data;
DROP TABLE flow_steps;
DROP TABLE chatbot_flows;

-- Remove the demo business that V8 seeded into every database, but only if it was never put
-- to use: seed email, no API key or Meta token, and nothing attached to it.
DELETE FROM businesses b
WHERE b.email = 'sample@example.com'
  AND b.name = 'Sample Business'
  AND b.api_key_hash IS NULL
  AND b.access_token IS NULL
  AND NOT EXISTS (SELECT 1 FROM customers c WHERE c.business_id = b.id)
  AND NOT EXISTS (SELECT 1 FROM conversations c WHERE c.business_id = b.id)
  AND NOT EXISTS (SELECT 1 FROM messages m WHERE m.business_id = b.id)
  AND NOT EXISTS (SELECT 1 FROM categories c WHERE c.business_id = b.id)
  AND NOT EXISTS (SELECT 1 FROM products p WHERE p.business_id = b.id)
  AND NOT EXISTS (SELECT 1 FROM orders o WHERE o.business_id = b.id);
