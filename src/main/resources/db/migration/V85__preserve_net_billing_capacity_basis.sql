-- Additive metadata only: legacy allocation amounts and discount rows are untouched.
ALTER TABLE billing_allocation
    ADD COLUMN net_billing_gross_amount NUMERIC(19,2),
    ADD COLUMN net_billing_discount_amount NUMERIC(19,2),
    ADD CONSTRAINT ck_billing_net_basis CHECK (
        (net_billing_gross_amount IS NULL AND net_billing_discount_amount IS NULL)
        OR (net_billing_gross_amount IS NOT NULL AND net_billing_discount_amount IS NOT NULL
            AND allocation_type='SERVICE' AND allocated_amount>0
            AND net_billing_gross_amount>=allocated_amount AND net_billing_discount_amount>=0
            AND net_billing_gross_amount=allocated_amount+net_billing_discount_amount)
    );

CREATE FUNCTION protect_billing_allocation_net_basis() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Billing allocations cannot be physically deleted'; END IF;
    IF ROW(OLD.net_billing_gross_amount,OLD.net_billing_discount_amount)
        IS DISTINCT FROM ROW(NEW.net_billing_gross_amount,NEW.net_billing_discount_amount) THEN
        RAISE EXCEPTION 'Billing capacity basis is immutable';
    END IF;
    IF OLD.net_billing_gross_amount IS NOT NULL AND
        ROW(OLD.company_id,OLD.billing_installment_id,OLD.contract_id,OLD.contract_service_id,
            OLD.measurement_contract_composition_id,OLD.allocation_type,OLD.allocated_amount)
        IS DISTINCT FROM ROW(NEW.company_id,NEW.billing_installment_id,NEW.contract_id,NEW.contract_service_id,
            NEW.measurement_contract_composition_id,NEW.allocation_type,NEW.allocated_amount) THEN
        RAISE EXCEPTION 'Net billing financial origin is immutable';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_billing_allocation_net_basis BEFORE UPDATE OR DELETE ON billing_allocation
    FOR EACH ROW EXECUTE FUNCTION protect_billing_allocation_net_basis();

CREATE TABLE net_billing_operation (
    company_id UUID NOT NULL,
    operation_id UUID NOT NULL,
    contract_id UUID NOT NULL,
    fingerprint VARCHAR(64) NOT NULL CHECK(fingerprint ~ '^[a-f0-9]{64}$'),
    billing_installment_id UUID,
    change_request_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(company_id,operation_id),
    FOREIGN KEY(contract_id,company_id) REFERENCES contract(id,company_id),
    FOREIGN KEY(billing_installment_id,company_id,contract_id) REFERENCES billing_installment(id,company_id,contract_id),
    FOREIGN KEY(change_request_id,company_id) REFERENCES change_request(id,company_id),
    CHECK(billing_installment_id IS NOT NULL OR change_request_id IS NOT NULL)
);
CREATE FUNCTION protect_net_billing_operation() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Net billing operation history is immutable'; END IF;
    IF ROW(OLD.company_id,OLD.operation_id,OLD.contract_id,OLD.fingerprint,OLD.change_request_id,OLD.created_at)
        IS DISTINCT FROM ROW(NEW.company_id,NEW.operation_id,NEW.contract_id,NEW.fingerprint,NEW.change_request_id,NEW.created_at)
        OR OLD.billing_installment_id IS NOT NULL OR NEW.billing_installment_id IS NULL THEN
        RAISE EXCEPTION 'Net billing operation can only complete once';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_net_billing_operation BEFORE UPDATE OR DELETE ON net_billing_operation
    FOR EACH ROW EXECUTE FUNCTION protect_net_billing_operation();
