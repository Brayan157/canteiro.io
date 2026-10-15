ALTER TABLE receivable_settlement ADD CONSTRAINT uk_receivable_settlement_id_company UNIQUE(id,company_id);
ALTER TABLE billing_allocation ADD CONSTRAINT uk_billing_allocation_id_company UNIQUE(id,company_id);
CREATE TABLE receivable_settlement_allocation (
 company_id UUID NOT NULL, settlement_id UUID NOT NULL,billing_allocation_id UUID NOT NULL,amount NUMERIC(19,2) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(settlement_id,billing_allocation_id),
 FOREIGN KEY(settlement_id,company_id) REFERENCES receivable_settlement(id,company_id),
 FOREIGN KEY(billing_allocation_id,company_id) REFERENCES billing_allocation(id,company_id)
);
CREATE TABLE receivable_credit (
 id UUID PRIMARY KEY,company_id UUID NOT NULL,receivable_id UUID NOT NULL,contract_id UUID NOT NULL,
 amount NUMERIC(19,2) NOT NULL CHECK(amount>0),reason VARCHAR(1000) NOT NULL CHECK(length(btrim(reason))>0),
 reversal_of_credit_id UUID,actor_user_id UUID,created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(id,company_id,receivable_id,contract_id),
 FOREIGN KEY(receivable_id,company_id,contract_id) REFERENCES receivable(id,company_id,contract_id),
 FOREIGN KEY(reversal_of_credit_id,company_id,receivable_id,contract_id) REFERENCES receivable_credit(id,company_id,receivable_id,contract_id)
);
CREATE UNIQUE INDEX uk_receivable_credit_reversal ON receivable_credit(company_id,reversal_of_credit_id) WHERE reversal_of_credit_id IS NOT NULL;
CREATE TRIGGER trg_receivable_credit_immutable BEFORE UPDATE OR DELETE ON receivable_credit
 FOR EACH ROW EXECUTE FUNCTION prevent_measurement_origin_ledger_mutation();
CREATE TRIGGER trg_receivable_attribution_immutable BEFORE UPDATE OR DELETE ON receivable_settlement_allocation
 FOR EACH ROW EXECUTE FUNCTION prevent_measurement_origin_ledger_mutation();
-- Historical cash entries retain their installment allocations, even if the installment was later cancelled.
WITH weights AS (
 SELECT s.id settlement_id,s.company_id,s.amount,b.id allocation_id,b.allocated_amount,
 SUM(b.allocated_amount) OVER(PARTITION BY s.id) total,
 ROW_NUMBER() OVER(PARTITION BY s.id ORDER BY b.id) rn,
 COUNT(*) OVER(PARTITION BY s.id) n
 FROM receivable_settlement s JOIN receivable r ON r.id=s.receivable_id AND r.company_id=s.company_id
 JOIN billing_allocation b ON b.billing_installment_id=r.billing_installment_id AND b.company_id=r.company_id AND b.allocation_type='SERVICE'
 WHERE s.entry_type='SETTLEMENT'
), rounded AS (
 SELECT w.*,TRUNC(allocated_amount*amount/total,2) share FROM weights w
)
INSERT INTO receivable_settlement_allocation(company_id,settlement_id,billing_allocation_id,amount)
SELECT company_id,settlement_id,allocation_id,CASE WHEN rn=n THEN amount-SUM(CASE WHEN rn<n THEN share ELSE 0 END) OVER(PARTITION BY settlement_id) ELSE share END FROM rounded;
INSERT INTO receivable_settlement_allocation(company_id,settlement_id,billing_allocation_id,amount)
SELECT s.company_id,s.id,a.billing_allocation_id,-a.amount FROM receivable_settlement s
JOIN receivable_settlement_allocation a ON a.settlement_id=s.reversal_of_settlement_id AND a.company_id=s.company_id WHERE s.entry_type='REVERSAL';

