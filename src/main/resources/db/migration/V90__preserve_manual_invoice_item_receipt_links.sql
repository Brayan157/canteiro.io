-- Additive only. Never infer invoice ownership for historical settlements.
ALTER TABLE invoice_item ADD CONSTRAINT uq_invoice_item_receipt_chain
    UNIQUE(id, company_id, contract_id, invoice_id, billing_allocation_id);

CREATE TABLE invoice_settlement_operation (
    company_id UUID NOT NULL,
    operation_id UUID NOT NULL,
    receivable_id UUID NOT NULL,
    contract_id UUID NOT NULL,
    fingerprint VARCHAR(64) NOT NULL CHECK(fingerprint ~ '^[a-f0-9]{64}$'),
    settlement_id UUID UNIQUE,
    change_request_id UUID UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(company_id, operation_id),
    FOREIGN KEY(receivable_id, company_id, contract_id) REFERENCES receivable(id, company_id, contract_id),
    FOREIGN KEY(settlement_id, company_id, receivable_id, contract_id)
        REFERENCES receivable_settlement(id, company_id, receivable_id, contract_id),
    FOREIGN KEY(change_request_id, company_id) REFERENCES change_request(id, company_id),
    CHECK(settlement_id IS NOT NULL OR change_request_id IS NOT NULL)
);

CREATE TABLE invoice_settlement_line (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    contract_id UUID NOT NULL,
    receivable_id UUID NOT NULL,
    settlement_id UUID NOT NULL,
    invoice_id UUID NOT NULL,
    invoice_item_id UUID NOT NULL,
    billing_allocation_id UUID NOT NULL,
    measurement_contract_composition_id UUID,
    amount NUMERIC(19,2) NOT NULL,
    reversal_of_line_id UUID UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(settlement_id, invoice_item_id),
    UNIQUE(id, company_id, receivable_id, contract_id, invoice_id, invoice_item_id, billing_allocation_id),
    FOREIGN KEY(settlement_id, company_id, receivable_id, contract_id)
        REFERENCES receivable_settlement(id, company_id, receivable_id, contract_id),
    FOREIGN KEY(invoice_item_id, company_id, contract_id, invoice_id, billing_allocation_id)
        REFERENCES invoice_item(id, company_id, contract_id, invoice_id, billing_allocation_id),
    FOREIGN KEY(reversal_of_line_id, company_id, receivable_id, contract_id, invoice_id, invoice_item_id, billing_allocation_id)
        REFERENCES invoice_settlement_line(id, company_id, receivable_id, contract_id, invoice_id, invoice_item_id, billing_allocation_id),
    CHECK((reversal_of_line_id IS NULL AND amount > 0) OR (reversal_of_line_id IS NOT NULL AND amount < 0))
);
CREATE INDEX ix_invoice_settlement_line_invoice ON invoice_settlement_line(company_id, invoice_id);
CREATE INDEX ix_invoice_settlement_line_receivable ON invoice_settlement_line(company_id, receivable_id);

CREATE FUNCTION validate_invoice_settlement_line() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM receivable_settlement s
        JOIN receivable r ON r.id=s.receivable_id AND r.company_id=s.company_id AND r.contract_id=s.contract_id
        JOIN billing_allocation a ON a.id=NEW.billing_allocation_id AND a.company_id=r.company_id
            AND a.contract_id=r.contract_id AND a.billing_installment_id=r.billing_installment_id AND a.allocation_type='SERVICE'
        WHERE s.id=NEW.settlement_id AND s.company_id=NEW.company_id
            AND a.measurement_contract_composition_id IS NOT DISTINCT FROM NEW.measurement_contract_composition_id
            AND ((s.entry_type='SETTLEMENT' AND NEW.reversal_of_line_id IS NULL)
                OR (s.entry_type='REVERSAL' AND EXISTS (
                    SELECT 1 FROM invoice_settlement_line original
                    WHERE original.id=NEW.reversal_of_line_id AND original.settlement_id=s.reversal_of_settlement_id
                        AND original.amount=-NEW.amount
                        AND original.measurement_contract_composition_id IS NOT DISTINCT FROM NEW.measurement_contract_composition_id
                )))
    ) THEN
        RAISE EXCEPTION 'Invoice receipt line must preserve its exact financial origin' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_invoice_settlement_line_chain BEFORE INSERT ON invoice_settlement_line
    FOR EACH ROW EXECUTE FUNCTION validate_invoice_settlement_line();
CREATE TRIGGER trg_invoice_settlement_line_immutable BEFORE UPDATE OR DELETE ON invoice_settlement_line
    FOR EACH ROW EXECUTE FUNCTION prevent_measurement_origin_ledger_mutation();

CREATE FUNCTION validate_invoice_settlement_line_total() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (SELECT COALESCE(SUM(amount),0) FROM invoice_settlement_line WHERE settlement_id=NEW.settlement_id AND company_id=NEW.company_id)
        <> (SELECT amount FROM receivable_settlement WHERE id=NEW.settlement_id AND company_id=NEW.company_id) THEN
        RAISE EXCEPTION 'Manual invoice receipt lines must equal the settlement amount' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE CONSTRAINT TRIGGER trg_invoice_settlement_line_total AFTER INSERT ON invoice_settlement_line
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_invoice_settlement_line_total();

CREATE FUNCTION protect_invoice_settlement_operation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Invoice receipt intent cannot be deleted' USING ERRCODE='23000'; END IF;
    IF (to_jsonb(NEW)-'settlement_id') IS DISTINCT FROM (to_jsonb(OLD)-'settlement_id')
        OR OLD.settlement_id IS NOT NULL OR NEW.settlement_id IS NULL THEN
        RAISE EXCEPTION 'Invoice receipt intent only allows completing its pending approval' USING ERRCODE='23000';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_invoice_settlement_operation_immutable BEFORE UPDATE OR DELETE ON invoice_settlement_operation
    FOR EACH ROW EXECUTE FUNCTION protect_invoice_settlement_operation();
