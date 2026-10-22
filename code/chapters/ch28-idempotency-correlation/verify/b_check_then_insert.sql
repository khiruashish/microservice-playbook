BEGIN;
SELECT count(*) = 0 AS fresh FROM payments WHERE strategy = 'B' AND idem_key = :'k' \gset
\if :fresh
  INSERT INTO provider_charges(strategy, idem_key) VALUES ('B', :'k');
  SELECT pg_sleep(0.02);
  INSERT INTO payments(strategy, idem_key, amount_paise) VALUES ('B', :'k', 64000);
  INSERT INTO outcomes VALUES ('B', :'k', '201');
\else
  INSERT INTO outcomes VALUES ('B', :'k', 'replayed');
\endif
COMMIT;
