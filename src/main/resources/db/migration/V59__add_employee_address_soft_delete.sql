ALTER TABLE employee_address
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX ix_employee_address_active
    ON employee_address (company_id, employee_id, active, primary_address DESC);
