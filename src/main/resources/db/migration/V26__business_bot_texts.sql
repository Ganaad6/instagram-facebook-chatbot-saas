-- The shop's own words in the bot: a greeting on the first menu, and a delivery note in the
-- order confirmation (when, where, how much). Empty means the built-in text / no note.
ALTER TABLE businesses ADD COLUMN welcome_message TEXT;
ALTER TABLE businesses ADD COLUMN delivery_note TEXT;
