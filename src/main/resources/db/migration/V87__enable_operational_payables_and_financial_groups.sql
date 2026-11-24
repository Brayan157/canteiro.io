-- Additive expansion of the tax-payable subset. No amount, status or origin is rewritten.
CREATE TABLE payable_financial_group (
    id uuid PRIMARY KEY,
    company_id uuid NOT NULL REFERENCES company(id),
    operation_id uuid,
    payload_hash varchar(64),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_payable_group_tenant UNIQUE(id, company_id),
    CONSTRAINT uq_payable_group_operation UNIQUE(company_id, operation_id),
    CONSTRAINT ck_payable_group_operation CHECK (
        (operation_id IS NULL AND payload_hash IS NULL)
        OR (operation_id IS NOT NULL AND payload_hash IS NOT NULL AND payload_hash ~ '^[a-f0-9]{64}$'))
);
ALTER TABLE payable ADD COLUMN financial_group_id uuid;
ALTER TABLE payable ADD COLUMN supplier_id uuid;
ALTER TABLE payable ADD COLUMN financial_category_id uuid;
ALTER TABLE payable ADD COLUMN issued_on date;
ALTER TABLE payable ADD COLUMN currency varchar(3);
ALTER TABLE payable ADD COLUMN version bigint NOT NULL DEFAULT 0;
INSERT INTO payable_financial_group(id,company_id,created_at)
    SELECT id,company_id,created_at FROM payable;
UPDATE payable p SET financial_group_id=p.id,
    currency=COALESCE((SELECT c.currency FROM contract c WHERE c.id=p.contract_id AND c.company_id=p.company_id),'BRL');
ALTER TABLE payable ALTER COLUMN financial_group_id SET NOT NULL;
ALTER TABLE payable ALTER COLUMN currency SET NOT NULL;
ALTER TABLE payable ADD CONSTRAINT uq_payable_tenant UNIQUE(id,company_id);
ALTER TABLE payable ADD CONSTRAINT ck_payable_currency CHECK(currency IN ('BRL','USD','EUR'));
ALTER TABLE payable ADD CONSTRAINT ck_payable_version CHECK(version>=0);
ALTER TABLE payable ADD CONSTRAINT ck_payable_tax_origin_links CHECK(tax_obligation_id IS NULL OR
    (financial_scope='WORK' AND work_id IS NOT NULL AND contract_id IS NOT NULL));
ALTER TABLE payable ADD CONSTRAINT fk_payable_group_tenant FOREIGN KEY(financial_group_id,company_id)
    REFERENCES payable_financial_group(id,company_id);
ALTER TABLE payable ADD CONSTRAINT fk_payable_supplier_tenant FOREIGN KEY(supplier_id,company_id)
    REFERENCES supplier(id,company_id);
ALTER TABLE payable ADD CONSTRAINT fk_payable_category_tenant FOREIGN KEY(financial_category_id,company_id)
    REFERENCES financial_category(id,company_id);
ALTER TABLE payable ADD CONSTRAINT fk_payable_contract_currency FOREIGN KEY(contract_id,company_id,currency)
    REFERENCES contract(id,company_id,currency);
-- Reuse an existing equivalent key when available, without changing its semantics.
CREATE UNIQUE INDEX uq_contract_payable_chain ON contract(id,company_id,work_id);
ALTER TABLE payable ADD CONSTRAINT fk_payable_contract_work_tenant FOREIGN KEY(contract_id,company_id,work_id)
    REFERENCES contract(id,company_id,work_id);
CREATE INDEX ix_payable_group ON payable(company_id,financial_group_id,id);
CREATE INDEX ix_payable_supplier ON payable(company_id,supplier_id,due_on,id);
CREATE INDEX ix_payable_category ON payable(company_id,financial_category_id,due_on,id);
CREATE FUNCTION protect_operational_payable_history() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Payable history cannot be physically deleted' USING ERRCODE='23514'; END IF;
    IF TG_TABLE_NAME='payable_financial_group' THEN
        RAISE EXCEPTION 'Payable creation group is immutable' USING ERRCODE='23514';
    END IF;
    IF ROW(NEW.id,NEW.company_id,NEW.financial_scope,NEW.work_id,NEW.contract_id,NEW.tax_obligation_id,
           NEW.financial_group_id,NEW.supplier_id,NEW.financial_category_id,NEW.currency,NEW.created_at)
       IS DISTINCT FROM ROW(OLD.id,OLD.company_id,OLD.financial_scope,OLD.work_id,OLD.contract_id,OLD.tax_obligation_id,
           OLD.financial_group_id,OLD.supplier_id,OLD.financial_category_id,OLD.currency,OLD.created_at) THEN
        RAISE EXCEPTION 'Payable identity, scope, currency and origin are immutable' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END; $$;
CREATE TRIGGER protect_payable_history BEFORE UPDATE OR DELETE ON payable
    FOR EACH ROW EXECUTE FUNCTION protect_operational_payable_history();
CREATE TRIGGER protect_payable_group_history BEFORE UPDATE OR DELETE ON payable_financial_group
    FOR EACH ROW EXECUTE FUNCTION protect_operational_payable_history();
