ALTER TABLE measurement_contract_composition ADD CONSTRAINT uk_measurement_composition_full_origin
    UNIQUE (id,company_id,contract_id,contract_service_id);
ALTER TABLE billing_allocation ADD CONSTRAINT fk_billing_measurement_full_origin
    FOREIGN KEY(measurement_contract_composition_id,company_id,contract_id,contract_service_id)
    REFERENCES measurement_contract_composition(id,company_id,contract_id,contract_service_id);
ALTER TABLE billing_allocation ADD CONSTRAINT ck_billing_measurement_service_origin
    CHECK(measurement_contract_composition_id IS NULL OR allocation_type='SERVICE');
