ALTER TABLE tax_obligation ADD CONSTRAINT uk_tax_calculation_invoice_chain UNIQUE(id,company_id,contract_id,invoice_id);
CREATE TABLE invoice_tax_operation (
    company_id UUID NOT NULL,
    operation_id UUID NOT NULL,
    invoice_id UUID NOT NULL,
    contract_id UUID NOT NULL,
    fingerprint VARCHAR(64) NOT NULL CHECK (fingerprint ~ '^[a-f0-9]{64}$'),
    calculation_mode VARCHAR(20) NOT NULL CHECK (calculation_mode IN ('AMOUNT','PERCENTAGE')),
    base_amount NUMERIC(19,2),
    rate_percent NUMERIC(21,4),
    amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    tax_obligation_id UUID,
    change_request_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (company_id,operation_id),
    UNIQUE(tax_obligation_id),
    UNIQUE(change_request_id),
    FOREIGN KEY(invoice_id,company_id,contract_id) REFERENCES invoice(id,company_id,contract_id),
    FOREIGN KEY(tax_obligation_id,company_id,contract_id,invoice_id) REFERENCES tax_obligation(id,company_id,contract_id,invoice_id),
    FOREIGN KEY(change_request_id,company_id) REFERENCES change_request(id,company_id),
    CHECK (tax_obligation_id IS NOT NULL OR change_request_id IS NOT NULL),
    CHECK ((calculation_mode='AMOUNT' AND base_amount IS NULL AND rate_percent IS NULL)
        OR (calculation_mode='PERCENTAGE' AND base_amount>0 AND rate_percent>0
            AND base_amount IS NOT NULL AND rate_percent IS NOT NULL AND amount=ROUND(base_amount*rate_percent/100,2)))
);
CREATE FUNCTION protect_invoice_tax_operation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Invoice tax intent cannot be deleted' USING ERRCODE='23000'; END IF;
    IF (to_jsonb(NEW)-'tax_obligation_id') IS DISTINCT FROM (to_jsonb(OLD)-'tax_obligation_id')
        OR OLD.tax_obligation_id IS NOT NULL OR NEW.tax_obligation_id IS NULL THEN
        RAISE EXCEPTION 'Invoice tax intent is immutable except its one-time approval result' USING ERRCODE='23000';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_invoice_tax_operation_immutable BEFORE UPDATE OR DELETE ON invoice_tax_operation
FOR EACH ROW EXECUTE FUNCTION protect_invoice_tax_operation();
