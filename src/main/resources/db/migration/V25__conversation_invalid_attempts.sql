-- How many replies in a row the bot could not understand at the current step; after a couple
-- it offers a human instead of repeating the menu. Reset whenever the conversation moves on.
ALTER TABLE conversations ADD COLUMN invalid_attempts INT NOT NULL DEFAULT 0;
