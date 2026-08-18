CREATE TABLE trust_unlock (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES company(id),
    charge_id UUID NOT NULL,
    granted_by_user_id UUID NOT NULL REFERENCES app_user(id),
    reason VARCHAR(500) NOT NULL,
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_trust_unlock_reason_not_blank CHECK (LENGTH(BTRIM(reason)) > 0),
    CONSTRAINT ck_trust_unlock_period CHECK (expires_at > starts_at),
    CONSTRAINT fk_trust_unlock_charge_company FOREIGN KEY (charge_id, company_id)
        REFERENCES platform_charge(id, company_id)
);

CREATE INDEX ix_trust_unlock_charge_created_at
    ON trust_unlock (charge_id, created_at);

CREATE INDEX ix_trust_unlock_company_active
    ON trust_unlock (company_id, starts_at, expires_at);
