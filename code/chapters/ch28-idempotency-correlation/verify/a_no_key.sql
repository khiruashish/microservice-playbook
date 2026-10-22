INSERT INTO provider_charges(strategy, idem_key) VALUES ('A', :'k');
SELECT pg_sleep(0.02);
INSERT INTO payments(strategy, idem_key, amount_paise) VALUES ('A', :'k', 64000);
INSERT INTO outcomes VALUES ('A', :'k', '201');
