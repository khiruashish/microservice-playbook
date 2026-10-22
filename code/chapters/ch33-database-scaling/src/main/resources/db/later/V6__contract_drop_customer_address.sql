-- Contract: ships in the release after every instance runs with app.address-migration.phase=CONTRACT (no instance
-- reads or writes customers.address any more). Move this file into db/migration in that release.
ALTER TABLE customers DROP COLUMN address;
