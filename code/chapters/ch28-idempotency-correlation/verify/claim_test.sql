DROP TABLE IF EXISTS idempotency_keys;
CREATE TABLE idempotency_keys (
  client_id        text        NOT NULL,
  idempotency_key  text        NOT NULL,
  request_hash     text        NOT NULL,
  status           text        NOT NULL CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
  locked_until     timestamptz NOT NULL,
  response_status  int,
  response_body    text,
  created_at       timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (client_id, idempotency_key));
\echo '1. first claim wins'
INSERT INTO idempotency_keys (client_id, idempotency_key, request_hash, status, locked_until)
VALUES ('app', 'k1', 'h1', 'IN_PROGRESS', now() + make_interval(secs => 30))
ON CONFLICT (client_id, idempotency_key) DO UPDATE SET locked_until = EXCLUDED.locked_until
 WHERE idempotency_keys.status = 'IN_PROGRESS' AND idempotency_keys.locked_until < now()
   AND idempotency_keys.request_hash = EXCLUDED.request_hash
RETURNING idempotency_key;
\echo '2. second claim while the lease is valid: no row (caller reads the record -> 409)'
INSERT INTO idempotency_keys (client_id, idempotency_key, request_hash, status, locked_until)
VALUES ('app', 'k1', 'h1', 'IN_PROGRESS', now() + make_interval(secs => 30))
ON CONFLICT (client_id, idempotency_key) DO UPDATE SET locked_until = EXCLUDED.locked_until
 WHERE idempotency_keys.status = 'IN_PROGRESS' AND idempotency_keys.locked_until < now()
   AND idempotency_keys.request_hash = EXCLUDED.request_hash
RETURNING idempotency_key;
\echo '3. the owner crashed; lease expired: a retry reclaims it'
UPDATE idempotency_keys SET locked_until = now() - interval '1 second' WHERE idempotency_key = 'k1';
INSERT INTO idempotency_keys (client_id, idempotency_key, request_hash, status, locked_until)
VALUES ('app', 'k1', 'h1', 'IN_PROGRESS', now() + make_interval(secs => 30))
ON CONFLICT (client_id, idempotency_key) DO UPDATE SET locked_until = EXCLUDED.locked_until
 WHERE idempotency_keys.status = 'IN_PROGRESS' AND idempotency_keys.locked_until < now()
   AND idempotency_keys.request_hash = EXCLUDED.request_hash
RETURNING idempotency_key;
\echo '4. expired lease but a different request body: not reclaimed (caller -> 422)'
UPDATE idempotency_keys SET locked_until = now() - interval '1 second' WHERE idempotency_key = 'k1';
INSERT INTO idempotency_keys (client_id, idempotency_key, request_hash, status, locked_until)
VALUES ('app', 'k1', 'h2', 'IN_PROGRESS', now() + make_interval(secs => 30))
ON CONFLICT (client_id, idempotency_key) DO UPDATE SET locked_until = EXCLUDED.locked_until
 WHERE idempotency_keys.status = 'IN_PROGRESS' AND idempotency_keys.locked_until < now()
   AND idempotency_keys.request_hash = EXCLUDED.request_hash
RETURNING idempotency_key;
\echo '5. completed: never reclaimed (caller replays the stored response)'
UPDATE idempotency_keys SET status = 'COMPLETED', response_status = 201, response_body = '{"id":"p-1"}' WHERE idempotency_key = 'k1';
INSERT INTO idempotency_keys (client_id, idempotency_key, request_hash, status, locked_until)
VALUES ('app', 'k1', 'h1', 'IN_PROGRESS', now() + make_interval(secs => 30))
ON CONFLICT (client_id, idempotency_key) DO UPDATE SET locked_until = EXCLUDED.locked_until
 WHERE idempotency_keys.status = 'IN_PROGRESS' AND idempotency_keys.locked_until < now()
   AND idempotency_keys.request_hash = EXCLUDED.request_hash
RETURNING idempotency_key;
SELECT status, response_status, response_body FROM idempotency_keys;
