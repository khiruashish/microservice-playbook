\set ON_ERROR_STOP on
INSERT INTO provider_charges(strategy, idem_key) VALUES ('E', :'k');
SELECT pg_sleep(0.02);
INSERT INTO payments_unique(idem_key, amount_paise) VALUES (:'k', 64000);
