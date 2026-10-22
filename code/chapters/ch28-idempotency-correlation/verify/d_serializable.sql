\set ON_ERROR_STOP on
BEGIN ISOLATION LEVEL SERIALIZABLE;
SELECT count(*) = 0 AS fresh FROM payments WHERE strategy = 'D' AND idem_key = :'k' \gset
\if :fresh
  -- the provider call: an external side effect, which a rollback can't undo
  \setenv IDEM_KEY :k
  \! echo $IDEM_KEY >> provider_D.log
  SELECT pg_sleep(0.02);
  INSERT INTO payments(strategy, idem_key, amount_paise) VALUES ('D', :'k', 64000);
\endif
COMMIT;
