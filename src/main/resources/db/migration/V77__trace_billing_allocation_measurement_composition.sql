ALTER TABLE billing_allocation
    ADD COLUMN measurement_contract_composition_id UUID;

ALTER TABLE billing_allocation
    ADD CONSTRAINT fk_billing_allocation_measurement_composition_company
        FOREIGN KEY (measurement_contract_composition_id, company_id)
        REFERENCES measurement_contract_composition (id, company_id);

CREATE INDEX ix_billing_allocation_measurement_composition
    ON billing_allocation (company_id, measurement_contract_composition_id)
    WHERE measurement_contract_composition_id IS NOT NULL;
