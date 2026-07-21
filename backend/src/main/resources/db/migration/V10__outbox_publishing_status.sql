-- The relay marks a job PUBLISHING while it is in flight to the broker, so a later poll cannot
-- publish the same job a second time. A job stuck PUBLISHING is reset to PENDING by the relay.

ALTER TABLE outbox_jobs DROP CONSTRAINT IF EXISTS outbox_jobs_status_check;
ALTER TABLE outbox_jobs ADD CONSTRAINT outbox_jobs_status_check
    CHECK (status IN ('PENDING', 'PUBLISHING', 'SENT', 'DEAD'));
