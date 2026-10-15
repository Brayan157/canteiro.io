ALTER TABLE measurement_version
    ALTER COLUMN external_acceptance_notes TYPE VARCHAR(2000);

CREATE TABLE measurement_version_correction_item (
    measurement_version_id UUID NOT NULL,
    company_id UUID NOT NULL,
    measurement_item_id UUID NOT NULL,
    PRIMARY KEY (measurement_version_id, measurement_item_id),
    CONSTRAINT fk_measurement_version_correction_item_version_company
        FOREIGN KEY (measurement_version_id, company_id)
        REFERENCES measurement_version (id, company_id),
    CONSTRAINT fk_measurement_version_correction_item_item_version_company
        FOREIGN KEY (measurement_item_id, company_id, measurement_version_id)
        REFERENCES measurement_item (id, company_id, measurement_version_id)
);

CREATE INDEX ix_measurement_version_correction_item_company_version
    ON measurement_version_correction_item (company_id, measurement_version_id);

CREATE OR REPLACE FUNCTION prevent_accepted_measurement_item_change()
RETURNS TRIGGER AS $$
DECLARE
    target_version_id UUID;
    target_company_id UUID;
    target_status VARCHAR(30);
BEGIN
    IF TG_OP = 'DELETE' THEN
        target_version_id := OLD.measurement_version_id;
        target_company_id := OLD.company_id;
    ELSE
        target_version_id := NEW.measurement_version_id;
        target_company_id := NEW.company_id;
    END IF;

    SELECT status INTO target_status
    FROM measurement_version
    WHERE id = target_version_id AND company_id = target_company_id;

    IF target_status IN ('ACCEPTED', 'REJECTED') THEN
        RAISE EXCEPTION 'Accepted or rejected measurement version items are immutable';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
