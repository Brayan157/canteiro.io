ALTER TABLE audit_event
    ALTER COLUMN actor_user_id DROP NOT NULL;

ALTER TABLE audit_event
    DROP CONSTRAINT ck_audit_event_actor_type,
    ADD CONSTRAINT ck_audit_event_actor_type CHECK (
        actor_type IN ('COMPANY_USER', 'PLATFORM_USER', 'SYSTEM')
    ),
    ADD CONSTRAINT ck_audit_event_actor_identity CHECK (
        (actor_type = 'SYSTEM' AND actor_user_id IS NULL)
        OR (actor_type IN ('COMPANY_USER', 'PLATFORM_USER') AND actor_user_id IS NOT NULL)
    );

ALTER TABLE platform_charge
    ADD CONSTRAINT uk_platform_charge_id_company UNIQUE (id, company_id);

CREATE TABLE company_subscription_access (
    company_id UUID PRIMARY KEY REFERENCES company(id),
    access_level VARCHAR(30) NOT NULL,
    restriction_charge_id UUID,
    effective_on DATE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_company_subscription_access_level CHECK (
        access_level IN ('FULL', 'READ_ONLY', 'DELINQUENT_READ_ONLY', 'BLOCKED')
    ),
    CONSTRAINT ck_company_subscription_access_restriction CHECK (
        (access_level = 'FULL' AND restriction_charge_id IS NULL)
        OR (access_level <> 'FULL' AND restriction_charge_id IS NOT NULL)
    ),
    CONSTRAINT fk_company_subscription_access_charge_company FOREIGN KEY (restriction_charge_id, company_id)
        REFERENCES platform_charge(id, company_id)
);

CREATE INDEX ix_company_subscription_access_level
    ON company_subscription_access (access_level);

CREATE TABLE platform_charge_notice (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES company(id),
    charge_id UUID NOT NULL,
    notice_type VARCHAR(30) NOT NULL,
    recipient_email VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL,
    occurred_on DATE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_platform_charge_notice_type CHECK (
        notice_type IN ('DUE_DATE', 'READ_ONLY', 'DELINQUENT', 'BLOCKED')
    ),
    CONSTRAINT ck_platform_charge_notice_status CHECK (
        status IN ('PENDING_DELIVERY')
    ),
    CONSTRAINT ck_platform_charge_notice_recipient CHECK (
        LENGTH(BTRIM(recipient_email)) > 0
    ),
    CONSTRAINT fk_platform_charge_notice_charge_company FOREIGN KEY (charge_id, company_id)
        REFERENCES platform_charge(id, company_id),
    CONSTRAINT uk_platform_charge_notice_type UNIQUE (charge_id, notice_type)
);

CREATE INDEX ix_platform_charge_notice_pending_delivery
    ON platform_charge_notice (status, created_at);
