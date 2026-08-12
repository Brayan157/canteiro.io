ALTER TABLE subscription
    ADD CONSTRAINT uk_subscription_id_company UNIQUE (id, company_id);

CREATE TABLE platform_charge (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES company(id),
    subscription_id UUID NOT NULL,
    provider VARCHAR(30) NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    external_customer_id VARCHAR(100) NOT NULL,
    external_charge_id VARCHAR(100) NOT NULL,
    billing_method VARCHAR(30) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    due_date DATE NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_platform_charge_provider CHECK (
        LENGTH(BTRIM(provider)) > 0 AND provider = UPPER(provider) AND provider ~ '^[A-Z0-9_]+$'
    ),
    CONSTRAINT ck_platform_charge_billing_method CHECK (
        billing_method IN ('CREDIT_CARD', 'PIX', 'BANK_SLIP')
    ),
    CONSTRAINT ck_platform_charge_status CHECK (
        status IN ('PENDING', 'CONFIRMED', 'OVERDUE', 'CANCELLED')
    ),
    CONSTRAINT ck_platform_charge_amount_non_negative CHECK (amount >= 0),
    CONSTRAINT fk_platform_charge_subscription_company FOREIGN KEY (subscription_id, company_id)
        REFERENCES subscription(id, company_id),
    CONSTRAINT uk_platform_charge_idempotency UNIQUE (provider, idempotency_key),
    CONSTRAINT uk_platform_charge_external_id UNIQUE (provider, external_charge_id)
);

CREATE INDEX ix_platform_charge_company_id ON platform_charge (company_id);
CREATE INDEX ix_platform_charge_subscription_id ON platform_charge (subscription_id);
CREATE INDEX ix_platform_charge_due_status ON platform_charge (due_date, status);

CREATE TABLE payment_gateway_event (
    id UUID PRIMARY KEY,
    provider VARCHAR(30) NOT NULL,
    external_event_id VARCHAR(150) NOT NULL,
    external_charge_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    attributes JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(30) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE,
    failure_reason VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_payment_gateway_event_provider CHECK (
        LENGTH(BTRIM(provider)) > 0 AND provider = UPPER(provider) AND provider ~ '^[A-Z0-9_]+$'
    ),
    CONSTRAINT ck_payment_gateway_event_type CHECK (
        event_type IN ('CHARGE_CREATED', 'CHARGE_CONFIRMED', 'CHARGE_OVERDUE', 'CHARGE_CANCELLED')
    ),
    CONSTRAINT ck_payment_gateway_event_status CHECK (status IN ('RECEIVED', 'PROCESSED', 'FAILED')),
    CONSTRAINT ck_payment_gateway_event_processing CHECK (
        (status = 'RECEIVED' AND processed_at IS NULL AND failure_reason IS NULL)
        OR (status = 'PROCESSED' AND processed_at IS NOT NULL AND failure_reason IS NULL)
        OR (status = 'FAILED' AND processed_at IS NOT NULL AND failure_reason IS NOT NULL)
    ),
    CONSTRAINT uk_payment_gateway_event_external_id UNIQUE (provider, external_event_id)
);

CREATE INDEX ix_payment_gateway_event_external_charge
    ON payment_gateway_event (provider, external_charge_id);
CREATE INDEX ix_payment_gateway_event_status_received
    ON payment_gateway_event (status, received_at);
