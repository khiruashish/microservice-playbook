-- the inbox: one row per event processed, so a redelivered event is recognised (ADR-015)
CREATE TABLE inbox (
    event_id     text PRIMARY KEY,
    processed_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE kitchen_ticket (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id      text NOT NULL,
    restaurant_id text NOT NULL,
    item_count    int NOT NULL CHECK (item_count > 0),
    created_at    timestamptz NOT NULL DEFAULT now()
);
