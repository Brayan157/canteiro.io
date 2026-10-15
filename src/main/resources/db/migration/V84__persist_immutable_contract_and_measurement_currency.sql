-- Legacy values remain numerically identical: BRL labels, not currency conversion.
ALTER TABLE contract ADD COLUMN currency varchar(3) NOT NULL DEFAULT 'BRL';
ALTER TABLE measurement ADD COLUMN currency varchar(3) NOT NULL DEFAULT 'BRL';
ALTER TABLE contract ADD CONSTRAINT ck_contract_currency CHECK (currency IN ('BRL', 'USD', 'EUR'));
ALTER TABLE measurement ADD CONSTRAINT ck_measurement_currency CHECK (currency IN ('BRL', 'USD', 'EUR'));
ALTER TABLE contract ADD CONSTRAINT uq_contract_tenant_currency UNIQUE (id, company_id, currency);
ALTER TABLE measurement ADD CONSTRAINT fk_measurement_contract_currency
    FOREIGN KEY (contract_id, company_id, currency) REFERENCES contract (id, company_id, currency);

-- Descendants inherit the immutable currency through their existing tenant/contract
-- or tenant/measurement chain, including historical versions and origin ledgers.
CREATE FUNCTION protect_monetary_root_currency() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.currency IS DISTINCT FROM OLD.currency THEN
        RAISE EXCEPTION 'Monetary root currency is immutable' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER protect_contract_currency BEFORE UPDATE OF currency ON contract
    FOR EACH ROW EXECUTE FUNCTION protect_monetary_root_currency();
CREATE TRIGGER protect_measurement_currency BEFORE UPDATE OF currency ON measurement
    FOR EACH ROW EXECUTE FUNCTION protect_monetary_root_currency();
