CREATE TABLE tax_obligation (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    invoice_id UUID NOT NULL,
    contract_id UUID NOT NULL,
    receivable_id UUID,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(1000),
    due_on DATE,
    amount NUMERIC(19, 2) NOT NULL,
    treatment VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'REGISTERED',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tax_obligation_id_company_contract UNIQUE (id, company_id, contract_id),
    CONSTRAINT fk_tax_obligation_invoice_company_contract
        FOREIGN KEY (invoice_id, company_id, contract_id)
        REFERENCES invoice (id, company_id, contract_id),
    CONSTRAINT fk_tax_obligation_receivable_company_contract
        FOREIGN KEY (receivable_id, company_id, contract_id)
        REFERENCES receivable (id, company_id, contract_id),
    CONSTRAINT ck_tax_obligation_name_not_blank CHECK (LENGTH(BTRIM(name)) > 0),
    CONSTRAINT ck_tax_obligation_description_not_blank CHECK (
        description IS NULL OR LENGTH(BTRIM(description)) > 0
    ),
    CONSTRAINT ck_tax_obligation_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_tax_obligation_treatment CHECK (
        treatment IN ('PAYABLE_BY_COMPANY', 'WITHHELD_BY_CUSTOMER', 'INFORMATIONAL')
    ),
    CONSTRAINT ck_tax_obligation_status CHECK (status IN ('REGISTERED', 'CANCELLED')),
    CONSTRAINT ck_tax_obligation_treatment_reference CHECK (
        (treatment = 'PAYABLE_BY_COMPANY' AND receivable_id IS NULL AND due_on IS NOT NULL)
        OR (treatment = 'WITHHELD_BY_CUSTOMER' AND receivable_id IS NOT NULL)
        OR (treatment = 'INFORMATIONAL' AND receivable_id IS NULL)
    )
);

CREATE INDEX ix_tax_obligation_company_invoice ON tax_obligation (company_id, invoice_id);
CREATE INDEX ix_tax_obligation_company_receivable_status ON tax_obligation (company_id, receivable_id, status)
    WHERE receivable_id IS NOT NULL;

CREATE TABLE payable (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    financial_scope VARCHAR(20) NOT NULL,
    work_id UUID,
    contract_id UUID,
    tax_obligation_id UUID,
    reference VARCHAR(100),
    description VARCHAR(1000),
    due_on DATE NOT NULL,
    original_amount NUMERIC(19, 2) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payable_work_company FOREIGN KEY (work_id, company_id)
        REFERENCES obra (id, company_id),
    CONSTRAINT fk_payable_contract_company FOREIGN KEY (contract_id, company_id)
        REFERENCES contract (id, company_id),
    CONSTRAINT fk_payable_tax_obligation_company_contract
        FOREIGN KEY (tax_obligation_id, company_id, contract_id)
        REFERENCES tax_obligation (id, company_id, contract_id),
    CONSTRAINT uk_payable_tax_obligation_company_contract UNIQUE (tax_obligation_id, company_id, contract_id),
    CONSTRAINT ck_payable_financial_scope CHECK (financial_scope IN ('WORK', 'COMPANY')),
    CONSTRAINT ck_payable_scope_links CHECK (
        (financial_scope = 'WORK' AND work_id IS NOT NULL)
        OR (financial_scope = 'COMPANY' AND work_id IS NULL AND contract_id IS NULL)
    ),
    CONSTRAINT ck_payable_reference_not_blank CHECK (reference IS NULL OR LENGTH(BTRIM(reference)) > 0),
    CONSTRAINT ck_payable_description_not_blank CHECK (description IS NULL OR LENGTH(BTRIM(description)) > 0),
    CONSTRAINT ck_payable_original_amount_positive CHECK (original_amount > 0),
    CONSTRAINT ck_payable_status CHECK (
        status IN ('DRAFT', 'OPEN', 'PARTIALLY_PAID', 'PAID', 'OVERDUE', 'RENEGOTIATED', 'CANCELLED')
    )
);

CREATE INDEX ix_payable_company_scope_status_due_on ON payable (company_id, financial_scope, status, due_on);
