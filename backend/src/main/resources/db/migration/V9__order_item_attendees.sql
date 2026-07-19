-- Attendee names ride along with the order line so a paid order can still issue its
-- tickets when payment lands, long after the buyer typed them.

ALTER TABLE order_items
    ADD COLUMN attendee_names TEXT[] NOT NULL DEFAULT '{}';
