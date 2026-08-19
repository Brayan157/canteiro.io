ALTER TABLE contract
    ADD CONSTRAINT uk_contract_id_company_work UNIQUE (id, company_id, work_id);

CREATE TABLE measurement (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    work_id UUID NOT NULL,
    contract_id UUID,
    reference VARCHAR(100),
    description VARCHAR(1000),
    measured_on DATE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_measurement_work_company FOREIGN KEY (work_id, company_id)
        REFERENCES obra (id, company_id),
    CONSTRAINT fk_measurement_contract_same_work FOREIGN KEY (contract_id, company_id, work_id)
        REFERENCES contract (id, company_id, work_id),
    CONSTRAINT ck_measurement_reference_not_blank CHECK (
        reference IS NULL OR LENGTH(BTRIM(reference)) > 0
    ),
    CONSTRAINT ck_measurement_description_not_blank CHECK (
        description IS NULL OR LENGTH(BTRIM(description)) > 0
    )
);

CREATE INDEX ix_measurement_company_work ON measurement (company_id, work_id);
CREATE INDEX ix_measurement_company_contract ON measurement (company_id, contract_id)
    WHERE contract_id IS NOT NULL;
