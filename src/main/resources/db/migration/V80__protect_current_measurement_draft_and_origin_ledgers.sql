CREATE OR REPLACE FUNCTION require_current_measurement_draft_item() RETURNS TRIGGER AS $$
DECLARE selected measurement_version%ROWTYPE;
BEGIN
 SELECT * INTO selected FROM measurement_version WHERE id=NEW.measurement_version_id AND company_id=NEW.company_id;
 IF selected.status='DRAFT' AND EXISTS(SELECT 1 FROM measurement_version v WHERE v.company_id=selected.company_id
   AND v.measurement_id=selected.measurement_id AND v.version_number>selected.version_number) THEN
   RAISE EXCEPTION 'Only the current measurement draft can be changed';
 END IF;
 RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_measurement_item_current_draft BEFORE INSERT OR UPDATE ON measurement_item
 FOR EACH ROW EXECUTE FUNCTION require_current_measurement_draft_item();
CREATE OR REPLACE FUNCTION protect_measurement_item_service_binding() RETURNS TRIGGER AS $$
DECLARE selected measurement_version%ROWTYPE;
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Measurement item service identity cannot be physically deleted'; END IF;
 IF TG_OP='UPDATE' AND OLD.continuous_service_id IS DISTINCT FROM NEW.continuous_service_id THEN
   SELECT * INTO selected FROM measurement_version WHERE id=NEW.measurement_version_id AND company_id=NEW.company_id;
   IF selected.status<>'DRAFT' OR EXISTS(SELECT 1 FROM measurement_version v WHERE v.company_id=selected.company_id
     AND v.measurement_id=selected.measurement_id AND v.version_number>selected.version_number) THEN
     RAISE EXCEPTION 'An accepted or historical item service binding is immutable';
   END IF;
 END IF;
 RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_measurement_item_service_immutable BEFORE UPDATE OR DELETE ON measurement_item_service
 FOR EACH ROW EXECUTE FUNCTION protect_measurement_item_service_binding();
CREATE TRIGGER trg_measurement_composition_immutable BEFORE UPDATE OR DELETE ON measurement_contract_composition
 FOR EACH ROW EXECUTE FUNCTION prevent_measurement_origin_ledger_mutation();
