ALTER TABLE platform_charge_notice
    ADD COLUMN delivery_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN last_attempt_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN delivered_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN failure_reason VARCHAR(500),
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    DROP CONSTRAINT ck_platform_charge_notice_status,
    ADD CONSTRAINT ck_platform_charge_notice_status CHECK (
        status IN ('PENDING_DELIVERY', 'DELIVERING', 'DELIVERED', 'DELIVERY_FAILED', 'CANCELLED')
    ),
    ADD CONSTRAINT ck_platform_charge_notice_delivery_attempts CHECK (delivery_attempts >= 0),
    ADD CONSTRAINT ck_platform_charge_notice_delivered_at CHECK (
        (status = 'DELIVERED' AND delivered_at IS NOT NULL)
        OR (status <> 'DELIVERED' AND delivered_at IS NULL)
    );

CREATE INDEX ix_platform_charge_notice_delivery_queue
    ON platform_charge_notice (status, last_attempt_at, created_at);
