DROP DATABASE IF EXISTS idem;
CREATE DATABASE idem;
\c idem
-- external side effects (the payment provider's view): one row per charge actually made
CREATE TABLE provider_charges (id bigserial PRIMARY KEY, strategy text, idem_key text, at timestamptz DEFAULT clock_timestamp());
-- our own payments table, per strategy
CREATE TABLE payments (id bigserial PRIMARY KEY, strategy text NOT NULL, idem_key text NOT NULL, amount_paise bigint NOT NULL);
-- strategy E: a unique constraint on our table
CREATE TABLE payments_unique (id bigserial PRIMARY KEY, idem_key text NOT NULL UNIQUE, amount_paise bigint NOT NULL);
-- strategy C: the idempotency key record (Stripe-style)
CREATE TABLE idempotency_keys (
  client_id text NOT NULL,
  idempotency_key text NOT NULL,
  request_hash text NOT NULL,
  status text NOT NULL CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
  response_status int,
  response_body text,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (client_id, idempotency_key));
CREATE TABLE outcomes (strategy text, idem_key text, outcome text);
