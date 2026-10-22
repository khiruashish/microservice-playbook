CREATE TABLE profiles (
    customer_id bigint      PRIMARY KEY,
    address     text        NOT NULL,
    version     int         NOT NULL DEFAULT 1,
    updated_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE loyalty (
    customer_id bigint PRIMARY KEY,
    points      int    NOT NULL DEFAULT 0 CHECK (points >= 0)
);
