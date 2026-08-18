CREATE TABLE contract_revision (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    contract_id UUID NOT NULL,
    revision_number INTEGER NOT NULL,
    previous_net_amount NUMERIC(19, 2) NOT NULL,
    proposed_net_amount NUMERIC(19, 2) NOT NULL,
    approved_billed_amount NUMERIC(19, 2) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_contract_revision_number UNIQUE (contract_id, revision_number),
    CONSTRAINT fk_contract_revision_contract_company FOREIGN KEY (contract_id, company_id)
        REFERENCES contract (id, company_id),
    CONSTRAINT ck_contract_revision_number_positive CHECK (revision_number > 0),
    CONSTRAINT ck_contract_revision_amounts_non_negative CHECK (
        previous_net_amount >= 0 AND proposed_net_amount >= 0 AND approved_billed_amount >= 0
    ),
    CONSTRAINT ck_contract_revision_billing_limit CHECK (proposed_net_amount >= approved_billed_amount),
    CONSTRAINT ck_contract_revision_reason_not_blank CHECK (LENGTH(BTRIM(reason)) > 0)
);

CREATE INDEX ix_contract_revision_contract ON contract_revision (company_id, contract_id, revision_number DESC);
