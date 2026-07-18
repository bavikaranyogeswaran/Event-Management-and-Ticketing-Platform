-- Source of the counter inside order numbers. Rolled-back orders leave gaps, which is expected.

CREATE SEQUENCE order_number_seq;
