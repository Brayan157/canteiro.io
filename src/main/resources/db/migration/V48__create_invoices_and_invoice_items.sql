ALTER TABLE billing_allocation
    ADD CONSTRAINT uk_billing_allocation_id_company_contract UNIQUE (id, company_id, contract_id);

CREATE TABLE invoice (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    contract_id UUID NOT NULL,
    number VARCHAR(100) NOT NULL,
    series VARCHAR(50),
    access_key VARCHAR(100),
    issued_on DATE NOT NULL,
    total_amount NUMERIC(19, 2) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_invoice_id_company_contract UNIQUE (id, company_id, contract_id),
    CONSTRAINT fk_invoice_contract_company FOREIGN KEY (contract_id, company_id)
        REFERENCES contract (id, company_id),
    CONSTRAINT ck_invoice_number_not_blank CHECK (LENGTH(BTRIM(number)) > 0),
    CONSTRAINT ck_invoice_series_not_blank CHECK (series IS NULL OR LENGTH(BTRIM(series)) > 0),
    CONSTRAINT ck_invoice_access_key_not_blank CHECK (access_key IS NULL OR LENGTH(BTRIM(access_key)) > 0),
    CONSTRAINT ck_invoice_total_amount_positive CHECK (total_amount > 0),
    CONSTRAINT ck_invoice_status CHECK (status IN ('DRAFT', 'REGISTERED', 'CANCELLED'))
);

CREATE UNIQUE INDEX ux_invoice_company_number_series
    ON invoice (company_id, number, COALESCE(series, ''));
CREATE UNIQUE INDEX ux_invoice_company_access_key
    ON invoice (company_id, access_key)
    WHERE access_key IS NOT NULL;
CREATE INDEX ix_invoice_company_contract_issued_on ON invoice (company_id, contract_id, issued_on);

CREATE TABLE invoice_item (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    invoice_id UUID NOT NULL,
    contract_id UUID NOT NULL,
    billing_allocation_id UUID NOT NULL,
    contract_service_id UUID,
    description VARCHAR(1000),
    allocated_amount NUMERIC(19, 2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_invoice_item_invoice_company_contract
        FOREIGN KEY (invoice_id, company_id, contract_id)
        REFERENCES invoice (id, company_id, contract_id),
    CONSTRAINT fk_invoice_item_allocation_company_contract
        FOREIGN KEY (billing_allocation_id, company_id, contract_id)
        REFERENCES billing_allocation (id, company_id, contract_id),
    CONSTRAINT fk_invoice_item_service_company_contract
        FOREIGN KEY (contract_service_id, company_id, contract_id)
        REFERENCES contract_service (id, company_id, contract_id),
    CONSTRAINT ck_invoice_item_description_not_blank CHECK (
        description IS NULL OR LENGTH(BTRIM(description)) > 0
    ),
    CONSTRAINT ck_invoice_item_allocated_amount_not_zero CHECK (allocated_amount <> 0)
);

CREATE INDEX ix_invoice_item_company_invoice ON invoice_item (company_id, invoice_id);
CREATE INDEX ix_invoice_item_company_allocation ON invoice_item (company_id, billing_allocation_id);
