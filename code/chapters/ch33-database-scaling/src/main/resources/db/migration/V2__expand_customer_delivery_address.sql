-- Expand: the new column, nullable and without a default, is a catalogue change only (instant on any size).
-- Old app versions don't know it exists; new ones write both columns until the contract step.
ALTER TABLE customers ADD COLUMN delivery_address text;
-- In the CONTRACT phase new rows no longer get an address; NOT NULL must go before that phase ships.
-- Dropping NOT NULL is a catalogue change too.
ALTER TABLE customers ALTER COLUMN address DROP NOT NULL;
