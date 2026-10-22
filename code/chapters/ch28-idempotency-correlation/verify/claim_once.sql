INSERT INTO idempotency_keys (client_id, idempotency_key, request_hash, status, locked_until)
VALUES ('app', :'k', 'h1', 'IN_PROGRESS', now() + make_interval(secs => 30))
ON CONFLICT (client_id, idempotency_key) DO UPDATE SET locked_until = EXCLUDED.locked_until
 WHERE idempotency_keys.status = 'IN_PROGRESS' AND idempotency_keys.locked_until < now()
   AND idempotency_keys.request_hash = EXCLUDED.request_hash
RETURNING idempotency_key \gset claimed_
\if :{?claimed_idempotency_key}
  INSERT INTO outcomes VALUES ('claim', :'k', 'claimed');
\else
  INSERT INTO outcomes VALUES ('claim', :'k', 'not claimed');
\endif
