ALTER TABLE measurement_item
    ADD CONSTRAINT uk_measurement_item_id_company UNIQUE (id, company_id);

ALTER TABLE contract_service
    ADD CONSTRAINT uk_contract_service_id_company UNIQUE (id, company_id);

CREATE TABLE measurement_item_contract_service_conversion (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    measurement_version_id UUID NOT NULL,
    measurement_item_id UUID NOT NULL,
    contract_id UUID NOT NULL,
    contract_service_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_measurement_item_conversion UNIQUE (company_id, measurement_item_id),
    CONSTRAINT uk_measurement_item_conversion_service UNIQUE (company_id, contract_service_id),
    CONSTRAINT fk_measurement_item_conversion_version_company FOREIGN KEY (measurement_version_id, company_id)
        REFERENCES measurement_version (id, company_id),
    CONSTRAINT fk_measurement_item_conversion_item_company FOREIGN KEY (measurement_item_id, company_id)
        REFERENCES measurement_item (id, company_id),
    CONSTRAINT fk_measurement_item_conversion_contract_company FOREIGN KEY (contract_id, company_id)
        REFERENCES contract (id, company_id),
    CONSTRAINT fk_measurement_item_conversion_service_company FOREIGN KEY (contract_service_id, company_id)
        REFERENCES contract_service (id, company_id)
);

CREATE INDEX ix_measurement_item_conversion_version
    ON measurement_item_contract_service_conversion (company_id, measurement_version_id);
