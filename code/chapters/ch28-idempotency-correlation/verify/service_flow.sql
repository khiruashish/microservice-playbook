DROP DATABASE IF EXISTS payments_check;
CREATE DATABASE payments_check;
\c payments_check
\i ../src/main/resources/db/migration/V1__payments_idempotency.sql
\set client 'app'
\set key 'k-9'
\set hash 'abc123'
\set leaseSeconds 30
\echo 'claim (PaymentService step 1):'
INSERT INTO idempotency_keys (client_id, idempotency_key, request_hash, status, locked_until)
VALUES (:'client', :'key', :'hash', 'IN_PROGRESS', now() + make_interval(secs => :leaseSeconds))
ON CONFLICT (client_id, idempotency_key) DO UPDATE SET locked_until = EXCLUDED.locked_until
 WHERE idempotency_keys.status = 'IN_PROGRESS' AND idempotency_keys.locked_until < now()
   AND idempotency_keys.request_hash = EXCLUDED.request_hash
RETURNING idempotency_key;
\echo 'record payment + complete (step 3, one transaction):'
BEGIN;
INSERT INTO payments (id, order_id, amount_paise, provider_reference) VALUES ('p-1', 'o-9001', 64000, 'ch_1');
UPDATE idempotency_keys SET status = 'COMPLETED', response_status = 201, response_body = '{"paymentId":"p-1"}'
 WHERE client_id = :'client' AND idempotency_key = :'key';
COMMIT;
\echo 'a late duplicate owner after a lease takeover can not record a second payment for the same charge:'
INSERT INTO payments (id, order_id, amount_paise, provider_reference) VALUES ('p-2', 'o-9001', 64000, 'ch_1');
\echo 'find (duplicate request):'
SELECT status, request_hash, response_status, response_body FROM idempotency_keys
 WHERE client_id = :'client' AND idempotency_key = :'key';
\echo 'release only affects IN_PROGRESS keys:'
DELETE FROM idempotency_keys WHERE client_id = :'client' AND idempotency_key = :'key' AND status = 'IN_PROGRESS';
\echo 'purge completed keys older than 24 h (none yet):'
DELETE FROM idempotency_keys WHERE status = 'COMPLETED' AND created_at < now() - make_interval(secs => 86400);
