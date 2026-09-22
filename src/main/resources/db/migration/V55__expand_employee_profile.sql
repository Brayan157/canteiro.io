ALTER TABLE employee
    ADD COLUMN registration_number VARCHAR(50),
    ADD COLUMN preferred_name VARCHAR(255),
    ADD COLUMN department VARCHAR(150),
    ADD COLUMN document_type VARCHAR(30),
    ADD COLUMN document_number VARCHAR(64),
    ADD COLUMN birth_date DATE,
    ADD COLUMN employment_type VARCHAR(30),
    ADD COLUMN employment_start_date DATE,
    ADD COLUMN employment_end_date DATE,
    ADD COLUMN base_monthly_salary NUMERIC(14, 2);

ALTER TABLE employee
    ADD CONSTRAINT ck_employee_document_complete
        CHECK ((document_type IS NULL AND document_number IS NULL)
            OR (document_type IS NOT NULL AND document_number IS NOT NULL)),
    ADD CONSTRAINT ck_employee_employment_dates
        CHECK (employment_end_date IS NULL
            OR employment_start_date IS NULL
            OR employment_end_date >= employment_start_date),
    ADD CONSTRAINT ck_employee_base_monthly_salary_positive
        CHECK (base_monthly_salary IS NULL OR base_monthly_salary > 0);

CREATE UNIQUE INDEX uk_employee_company_registration_number
    ON employee (company_id, registration_number)
    WHERE registration_number IS NOT NULL;

CREATE UNIQUE INDEX uk_employee_company_document
    ON employee (company_id, document_type, document_number)
    WHERE document_number IS NOT NULL;
