ALTER TABLE employee
    ADD COLUMN pis VARCHAR(11);

CREATE UNIQUE INDEX uk_employee_company_pis
    ON employee (company_id, pis)
    WHERE pis IS NOT NULL;
