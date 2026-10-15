ALTER TABLE receivable ADD COLUMN renegotiation_source_receivable_id UUID;
ALTER TABLE receivable ADD CONSTRAINT uk_receivable_id_origin_chain UNIQUE(id,company_id,contract_id,billing_installment_id);
ALTER TABLE receivable ADD CONSTRAINT fk_receivable_renegotiation_origin
 FOREIGN KEY(renegotiation_source_receivable_id,company_id,contract_id,billing_installment_id)
 REFERENCES receivable(id,company_id,contract_id,billing_installment_id);
ALTER TABLE receivable ADD CONSTRAINT ck_receivable_not_own_origin CHECK(renegotiation_source_receivable_id IS DISTINCT FROM id);
ALTER TABLE receivable DROP CONSTRAINT uk_receivable_billing_installment_company_contract;
CREATE UNIQUE INDEX uk_receivable_original_installment ON receivable(billing_installment_id,company_id,contract_id)
 WHERE renegotiation_source_receivable_id IS NULL;
CREATE INDEX ix_receivable_renegotiation_origin ON receivable(company_id,renegotiation_source_receivable_id);

CREATE FUNCTION validate_receipt_allocation_chain() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM receivable_settlement s
   JOIN receivable r ON r.id=s.receivable_id AND r.company_id=s.company_id AND r.contract_id=s.contract_id
   JOIN billing_allocation a ON a.id=NEW.billing_allocation_id AND a.company_id=r.company_id
      AND a.contract_id=r.contract_id AND a.billing_installment_id=r.billing_installment_id AND a.allocation_type='SERVICE'
   WHERE s.id=NEW.settlement_id AND s.company_id=NEW.company_id) THEN
  RAISE EXCEPTION 'Receipt allocation must preserve its installment and contract origin' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_receipt_allocation_chain BEFORE INSERT ON receivable_settlement_allocation
 FOR EACH ROW EXECUTE FUNCTION validate_receipt_allocation_chain();

CREATE FUNCTION protect_receivable_origin() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' OR NEW.renegotiation_source_receivable_id IS DISTINCT FROM OLD.renegotiation_source_receivable_id
    OR NEW.billing_installment_id IS DISTINCT FROM OLD.billing_installment_id
    OR NEW.contract_id IS DISTINCT FROM OLD.contract_id OR NEW.company_id IS DISTINCT FROM OLD.company_id
    OR NEW.original_amount IS DISTINCT FROM OLD.original_amount THEN
  RAISE EXCEPTION 'Receivable financial origin is immutable' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_receivable_origin_immutable BEFORE UPDATE OR DELETE ON receivable
 FOR EACH ROW EXECUTE FUNCTION protect_receivable_origin();
