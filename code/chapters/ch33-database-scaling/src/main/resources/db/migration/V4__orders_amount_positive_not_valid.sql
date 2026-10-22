-- NOT VALID: new and updated rows are checked from now on; existing rows are not scanned, so the exclusive lock
-- is held only for a moment. Validation happens in V5, in its own transaction.
ALTER TABLE orders ADD CONSTRAINT orders_amount_positive CHECK (amount_paise > 0) NOT VALID;
