CREATE TABLE obra (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    final_customer_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    reference VARCHAR(100),
    status VARCHAR(20) NOT NULL,
    started_on DATE,
    expected_completion_on DATE,
    completed_on DATE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_obra_final_customer_company FOREIGN KEY (final_customer_id, company_id)
        REFERENCES final_customer (id, company_id),
    CONSTRAINT ck_obra_name_not_blank CHECK (LENGTH(BTRIM(name)) > 0),
    CONSTRAINT ck_obra_status CHECK (status IN ('DRAFT', 'OPEN', 'ACTIVE', 'COMPLETED', 'FINALIZED', 'SUSPENDED', 'CANCELLED')),
    CONSTRAINT ck_obra_expected_completion CHECK (expected_completion_on IS NULL OR started_on IS NULL OR expected_completion_on >= started_on),
    CONSTRAINT ck_obra_completed_on CHECK (completed_on IS NULL OR started_on IS NULL OR completed_on >= started_on)
);

CREATE INDEX ix_obra_company_status ON obra (company_id, status);
CREATE INDEX ix_obra_customer ON obra (company_id, final_customer_id);
