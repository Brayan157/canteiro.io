ALTER TABLE obra
    ADD CONSTRAINT uk_obra_id_company UNIQUE (id, company_id);

CREATE TABLE contract (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    work_id UUID NOT NULL,
    reference VARCHAR(100),
    name VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    started_on DATE,
    expected_completion_on DATE,
    completed_on DATE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_contract_work_company FOREIGN KEY (work_id, company_id)
        REFERENCES obra (id, company_id),
    CONSTRAINT ck_contract_name_not_blank CHECK (LENGTH(BTRIM(name)) > 0),
    CONSTRAINT ck_contract_status CHECK (status IN ('DRAFT', 'OPEN', 'ACTIVE', 'COMPLETED', 'FINALIZED', 'SUSPENDED', 'CANCELLED')),
    CONSTRAINT ck_contract_expected_completion CHECK (
        expected_completion_on IS NULL OR started_on IS NULL OR expected_completion_on >= started_on
    ),
    CONSTRAINT ck_contract_completed_on CHECK (
        completed_on IS NULL OR started_on IS NULL OR completed_on >= started_on
    )
);

CREATE INDEX ix_contract_company_status ON contract (company_id, status);
CREATE INDEX ix_contract_work ON contract (company_id, work_id);
