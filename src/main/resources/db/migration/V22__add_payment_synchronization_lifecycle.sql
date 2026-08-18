ALTER TABLE platform_charge
    ADD COLUMN last_gateway_event_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE payment_gateway_event
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE subscription
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE INDEX ix_platform_charge_reconciliation
    ON platform_charge (status, updated_at)
    WHERE status IN ('PENDING', 'OVERDUE');

CREATE INDEX ix_payment_gateway_event_retry
    ON payment_gateway_event (status, processed_at, received_at)
    WHERE status IN ('RECEIVED', 'FAILED');
