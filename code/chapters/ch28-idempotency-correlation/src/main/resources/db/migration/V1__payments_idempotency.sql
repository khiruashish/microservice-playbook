-- One row per (client, key): the claim, the request fingerprint, and the stored response.
CREATE TABLE idempotency_keys (
  client_id        text        NOT NULL,
  idempotency_key  text        NOT NULL,
  request_hash     text        NOT NULL,
  status           text        NOT NULL CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
  locked_until     timestamptz NOT NULL,
  response_status  int,
  response_body    text,
  created_at       timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (client_id, idempotency_key)
);
CREATE INDEX idempotency_keys_created_at ON idempotency_keys (created_at);

CREATE TABLE payments (
  id                 text        PRIMARY KEY,
  order_id           text        NOT NULL,
  amount_paise       bigint      NOT NULL CHECK (amount_paise > 0),
  provider_reference text        NOT NULL UNIQUE,
  created_at         timestamptz NOT NULL DEFAULT now()
);
