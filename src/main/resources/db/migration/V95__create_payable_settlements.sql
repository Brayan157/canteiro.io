CREATE TABLE payable_settlement_operation (
    company_id UUID NOT NULL,
    operation_id UUID NOT NULL,
    payable_id UUID NOT NULL,
    fingerprint VARCHAR(64) NOT NULL CHECK (fingerprint ~ '^[0-9a-f]{64}$'),
    change_request_id UUID UNIQUE,
    PRIMARY KEY (company_id, operation_id),
    UNIQUE (company_id, operation_id, payable_id),
    FOREIGN KEY (payable_id, company_id) REFERENCES payable(id, company_id),
    FOREIGN KEY (change_request_id, company_id) REFERENCES change_request(id, company_id)
);

CREATE TABLE payable_settlement (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    payable_id UUID NOT NULL,
    operation_id UUID NOT NULL,
    reversal_of_id UUID UNIQUE,
    amount NUMERIC(19, 2) NOT NULL,
    effective_on DATE NOT NULL,
    payment_method VARCHAR(20) NOT NULL CHECK (payment_method IN ('PIX','BANK_TRANSFER','BOLETO','CARD','CASH','CHECK','OTHER')),
    recorded_by UUID NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    UNIQUE (company_id, operation_id),
    UNIQUE (id, company_id, payable_id),
    FOREIGN KEY (company_id, operation_id, payable_id)
        REFERENCES payable_settlement_operation(company_id, operation_id, payable_id),
    FOREIGN KEY (reversal_of_id, company_id, payable_id) REFERENCES payable_settlement(id, company_id, payable_id),
    FOREIGN KEY (recorded_by, company_id) REFERENCES company_user(user_id, company_id),
    CHECK ((reversal_of_id IS NULL AND amount > 0) OR (reversal_of_id IS NOT NULL AND amount < 0)),
    CHECK (reversal_of_id IS DISTINCT FROM id)
);

CREATE INDEX ix_payable_settlement_history
    ON payable_settlement(company_id, payable_id, effective_on DESC, recorded_at DESC, id DESC);

CREATE FUNCTION prevent_payable_settlement_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Payable settlement history is immutable' USING ERRCODE = '23514';
END;
$$;

CREATE TRIGGER trg_payable_settlement_immutable BEFORE UPDATE OR DELETE ON payable_settlement
    FOR EACH ROW EXECUTE FUNCTION prevent_payable_settlement_change();
CREATE TRIGGER trg_payable_settlement_operation_immutable BEFORE UPDATE OR DELETE ON payable_settlement_operation
    FOR EACH ROW EXECUTE FUNCTION prevent_payable_settlement_change();
