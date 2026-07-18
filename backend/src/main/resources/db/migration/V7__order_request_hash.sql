-- Idempotency fingerprint: a same-key retry with a different request is a conflict, not a replay.

ALTER TABLE orders
    ADD COLUMN request_hash VARCHAR(64) NOT NULL;
