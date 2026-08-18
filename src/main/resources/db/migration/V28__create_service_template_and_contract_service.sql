ALTER TABLE contract
    ADD CONSTRAINT uk_contract_id_company UNIQUE (id, company_id);

CREATE TABLE service_template (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES company (id),
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_service_template_id_company UNIQUE (id, company_id),
    CONSTRAINT uk_service_template_company_name UNIQUE (company_id, name),
    CONSTRAINT ck_service_template_name_not_blank CHECK (LENGTH(BTRIM(name)) > 0),
    CONSTRAINT ck_service_template_description_not_blank CHECK (
        description IS NULL OR LENGTH(BTRIM(description)) > 0
    )
);

CREATE TABLE contract_service (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    contract_id UUID NOT NULL,
    service_template_id UUID,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_contract_service_contract_company FOREIGN KEY (contract_id, company_id)
        REFERENCES contract (id, company_id),
    CONSTRAINT fk_contract_service_template_company FOREIGN KEY (service_template_id, company_id)
        REFERENCES service_template (id, company_id),
    CONSTRAINT ck_contract_service_name_not_blank CHECK (LENGTH(BTRIM(name)) > 0),
    CONSTRAINT ck_contract_service_description_not_blank CHECK (
        description IS NULL OR LENGTH(BTRIM(description)) > 0
    )
);

CREATE INDEX ix_service_template_company_active ON service_template (company_id, active);
CREATE INDEX ix_contract_service_contract ON contract_service (company_id, contract_id);
