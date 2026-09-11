CREATE INDEX ix_billing_allocation_company_contract_status
    ON billing_allocation (company_id, contract_id, status);
