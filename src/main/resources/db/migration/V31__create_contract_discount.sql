CREATE TABLE contract_discount (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    contract_id UUID NOT NULL,
    discount_type VARCHAR(20) NOT NULL,
    discount_value NUMERIC(19, 4) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_contract_discount_contract_company UNIQUE (contract_id, company_id),
    CONSTRAINT fk_contract_discount_contract_company FOREIGN KEY (contract_id, company_id)
        REFERENCES contract (id, company_id),
    CONSTRAINT ck_contract_discount_type CHECK (discount_type IN ('FIXED', 'PERCENTAGE')),
    CONSTRAINT ck_contract_discount_value_positive CHECK (discount_value > 0),
    CONSTRAINT ck_contract_discount_percentage_limit CHECK (
        discount_type <> 'PERCENTAGE' OR discount_value <= 100
    )
);
