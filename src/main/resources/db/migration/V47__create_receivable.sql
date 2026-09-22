CREATE TABLE receivable (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    contract_id UUID NOT NULL,
    billing_installment_id UUID NOT NULL,
    financial_group_id UUID NOT NULL,
    reference VARCHAR(100),
    description VARCHAR(1000),
    issued_on DATE,
    due_on DATE NOT NULL,
    original_amount NUMERIC(19, 2) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_receivable_billing_installment_company_contract
        UNIQUE (billing_installment_id, company_id, contract_id),
    CONSTRAINT fk_receivable_billing_installment_company_contract
        FOREIGN KEY (billing_installment_id, company_id, contract_id)
        REFERENCES billing_installment (id, company_id, contract_id),
    CONSTRAINT ck_receivable_reference_not_blank CHECK (
        reference IS NULL OR LENGTH(BTRIM(reference)) > 0
    ),
    CONSTRAINT ck_receivable_description_not_blank CHECK (
        description IS NULL OR LENGTH(BTRIM(description)) > 0
    ),
    CONSTRAINT ck_receivable_due_on CHECK (issued_on IS NULL OR due_on >= issued_on),
    CONSTRAINT ck_receivable_original_amount_positive CHECK (original_amount > 0),
    CONSTRAINT ck_receivable_status CHECK (
        status IN ('OPEN', 'PARTIALLY_RECEIVED', 'RECEIVED', 'OVERDUE', 'RENEGOTIATED', 'CANCELLED')
    )
);

CREATE INDEX ix_receivable_company_status_due_on ON receivable (company_id, status, due_on);
CREATE INDEX ix_receivable_company_contract ON receivable (company_id, contract_id);
CREATE INDEX ix_receivable_company_financial_group ON receivable (company_id, financial_group_id);
