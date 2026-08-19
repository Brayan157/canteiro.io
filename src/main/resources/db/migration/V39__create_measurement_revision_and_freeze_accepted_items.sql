ALTER TABLE measurement
    ADD COLUMN lock_version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE measurement_version
    ADD COLUMN previous_version_id UUID,
    ADD CONSTRAINT fk_measurement_version_previous_company FOREIGN KEY (previous_version_id, company_id)
        REFERENCES measurement_version (id, company_id);

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

    IF target_status = 'ACCEPTED' THEN
        RAISE EXCEPTION 'Accepted measurement version items are immutable';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_measurement_item_prevent_accepted_change
BEFORE INSERT OR UPDATE OR DELETE ON measurement_item
FOR EACH ROW EXECUTE FUNCTION prevent_accepted_measurement_item_change();
