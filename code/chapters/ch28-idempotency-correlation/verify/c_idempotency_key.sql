-- Transaction 1: claim the key (or find it). Committed before calling the provider.
INSERT INTO idempotency_keys(client_id, idempotency_key, request_hash, status)
VALUES ('app', :'k', md5('order=o-1;amount=64000'), 'IN_PROGRESS')
ON CONFLICT DO NOTHING
RETURNING true AS won \gset
\if :{?won}
  -- We own the key: call the provider (outside any transaction), then record the result.
  INSERT INTO provider_charges(strategy, idem_key) VALUES ('C', :'k');
  SELECT pg_sleep(0.02);
  BEGIN;
  INSERT INTO payments(strategy, idem_key, amount_paise) VALUES ('C', :'k', 64000);
  UPDATE idempotency_keys SET status = 'COMPLETED', response_status = 201, response_body = '{"payment":"ok"}'
   WHERE client_id = 'app' AND idempotency_key = :'k';
  COMMIT;
  INSERT INTO outcomes VALUES ('C', :'k', '201');
\else
  SELECT status = 'COMPLETED' AS done, request_hash = md5('order=o-1;amount=64000') AS same
    FROM idempotency_keys WHERE client_id = 'app' AND idempotency_key = :'k' \gset
  \if :same
    \if :done
      INSERT INTO outcomes VALUES ('C', :'k', '201 replayed');
    \else
      INSERT INTO outcomes VALUES ('C', :'k', '409 in progress');
    \endif
  \else
    INSERT INTO outcomes VALUES ('C', :'k', '422 different request');
  \endif
\endif
