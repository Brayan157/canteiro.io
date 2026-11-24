-- No backfill: the registration position of old invoices cannot be reconstructed safely.
CREATE TABLE invoice_financial_snapshot (
    invoice_id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    contract_id UUID NOT NULL,
    currency VARCHAR(3) NOT NULL CHECK (currency IN ('BRL','USD','EUR')),
    observed_at TIMESTAMPTZ NOT NULL,
    basis VARCHAR(64) NOT NULL CHECK (basis = 'CONTRACT_NET_MINUS_CONFIRMED_CASH_V1'),
    contract_net_amount NUMERIC(19,2) NOT NULL,
    confirmed_received_amount NUMERIC(19,2) NOT NULL,
    unreceived_cash_amount NUMERIC(19,2) NOT NULL,
    CONSTRAINT fk_invoice_snapshot_invoice FOREIGN KEY (invoice_id,company_id,contract_id)
        REFERENCES invoice(id,company_id,contract_id),
    CONSTRAINT fk_invoice_snapshot_currency FOREIGN KEY (contract_id,company_id,currency)
        REFERENCES contract(id,company_id,currency),
    CONSTRAINT ck_invoice_snapshot_difference CHECK
        (unreceived_cash_amount = contract_net_amount - confirmed_received_amount)
);

CREATE FUNCTION prevent_invoice_snapshot_mutation() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Invoice registration financial snapshots are immutable';
END;
$$;
CREATE TRIGGER invoice_snapshot_immutable BEFORE UPDATE OR DELETE ON invoice_financial_snapshot
    FOR EACH ROW EXECUTE FUNCTION prevent_invoice_snapshot_mutation();
