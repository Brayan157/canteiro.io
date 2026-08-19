ALTER TABLE measurement_item
    ADD CONSTRAINT uk_measurement_item_id_company_version UNIQUE (id, company_id, measurement_version_id);

ALTER TABLE measurement_item_contract_service_conversion
    ADD CONSTRAINT fk_measurement_item_conversion_item_version_company
        FOREIGN KEY (measurement_item_id, company_id, measurement_version_id)
        REFERENCES measurement_item (id, company_id, measurement_version_id);

CREATE TABLE measurement_contract_adjustment (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    measurement_version_id UUID NOT NULL,
    contract_id UUID NOT NULL,
    adjustment_amount NUMERIC(19, 2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_measurement_contract_adjustment_version UNIQUE (company_id, measurement_version_id),
    CONSTRAINT ck_measurement_contract_adjustment_amount_positive CHECK (adjustment_amount > 0),
    CONSTRAINT fk_measurement_contract_adjustment_version_company FOREIGN KEY (measurement_version_id, company_id)
        REFERENCES measurement_version (id, company_id),
    CONSTRAINT fk_measurement_contract_adjustment_contract_company FOREIGN KEY (contract_id, company_id)
        REFERENCES contract (id, company_id)
);

CREATE INDEX ix_measurement_contract_adjustment_company_contract
    ON measurement_contract_adjustment (company_id, contract_id);

CREATE OR REPLACE FUNCTION prevent_non_draft_measurement_discount_change()
RETURNS TRIGGER AS $$
DECLARE
    target_version_id UUID;
    target_company_id UUID;
    version_status VARCHAR(30);
BEGIN
    IF TG_OP = 'DELETE' THEN
        target_version_id := OLD.measurement_version_id;
        target_company_id := OLD.company_id;
    ELSE
        target_version_id := NEW.measurement_version_id;
        target_company_id := NEW.company_id;
    END IF;

    SELECT status INTO version_status
    FROM measurement_version
    WHERE id = target_version_id AND company_id = target_company_id;

    IF version_status IS DISTINCT FROM 'DRAFT' THEN
        RAISE EXCEPTION 'Measurement header discounts can only be changed while the version is draft';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_measurement_discount_prevent_non_draft_change
BEFORE INSERT OR UPDATE OR DELETE ON measurement_discount
FOR EACH ROW EXECUTE FUNCTION prevent_non_draft_measurement_discount_change();
