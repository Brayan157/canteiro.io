ALTER TABLE contract_service
    ADD CONSTRAINT uk_contract_service_id_company_contract UNIQUE (id, company_id, contract_id);

CREATE TABLE billing_installment (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    contract_id UUID NOT NULL,
    reference VARCHAR(100),
    description VARCHAR(1000),
    issued_on DATE,
    due_on DATE NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_billing_installment_id_company_contract UNIQUE (id, company_id, contract_id),
    CONSTRAINT fk_billing_installment_contract_company FOREIGN KEY (contract_id, company_id)
        REFERENCES contract (id, company_id),
    CONSTRAINT ck_billing_installment_reference_not_blank CHECK (
        reference IS NULL OR LENGTH(BTRIM(reference)) > 0
    ),
    CONSTRAINT ck_billing_installment_description_not_blank CHECK (
        description IS NULL OR LENGTH(BTRIM(description)) > 0
    ),
    CONSTRAINT ck_billing_installment_due_on CHECK (
        issued_on IS NULL OR due_on >= issued_on
    ),
    CONSTRAINT ck_billing_installment_status CHECK (
        status IN ('DRAFT', 'OPEN', 'PARTIALLY_RECEIVED', 'RECEIVED', 'OVERDUE', 'RENEGOTIATED', 'CANCELLED')
    )
);

CREATE INDEX ix_billing_installment_company_contract ON billing_installment (company_id, contract_id);
CREATE INDEX ix_billing_installment_company_status_due_on ON billing_installment (company_id, status, due_on);

CREATE TABLE billing_allocation (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    billing_installment_id UUID NOT NULL,
    contract_id UUID NOT NULL,
    contract_service_id UUID NOT NULL,
    allocated_amount NUMERIC(19, 2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_billing_allocation_installment_company_contract
        FOREIGN KEY (billing_installment_id, company_id, contract_id)
        REFERENCES billing_installment (id, company_id, contract_id),
    CONSTRAINT fk_billing_allocation_service_company_contract
        FOREIGN KEY (contract_service_id, company_id, contract_id)
        REFERENCES contract_service (id, company_id, contract_id),
    CONSTRAINT ck_billing_allocation_amount_positive CHECK (allocated_amount > 0),
    CONSTRAINT ck_billing_allocation_status CHECK (status IN ('DRAFT', 'ACTIVE', 'CANCELLED'))
);

CREATE INDEX ix_billing_allocation_company_contract ON billing_allocation (company_id, contract_id);
CREATE INDEX ix_billing_allocation_installment ON billing_allocation (company_id, billing_installment_id);
CREATE INDEX ix_billing_allocation_service ON billing_allocation (company_id, contract_service_id);
