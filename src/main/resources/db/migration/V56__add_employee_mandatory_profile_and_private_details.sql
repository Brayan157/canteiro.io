CREATE SEQUENCE employee_registration_number_seq START WITH 1;

ALTER TABLE employee
    RENAME COLUMN base_monthly_salary TO compensation_amount;

ALTER TABLE employee
    ADD COLUMN currently_employed BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN gender VARCHAR(30),
    ADD COLUMN ethnicity VARCHAR(30),
    ADD COLUMN compensation_frequency VARCHAR(30);

ALTER TABLE employee
    ADD CONSTRAINT ck_employee_compensation_amount_positive
        CHECK (compensation_amount IS NULL OR compensation_amount > 0);

CREATE TABLE employee_address (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    employee_id UUID NOT NULL,
    label VARCHAR(100),
    postal_code VARCHAR(20),
    street VARCHAR(255) NOT NULL,
    number VARCHAR(40),
    complement VARCHAR(255),
    district VARCHAR(150),
    city VARCHAR(150) NOT NULL,
    state VARCHAR(100),
    country VARCHAR(100) NOT NULL,
    primary_address BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_employee_address_company FOREIGN KEY (company_id) REFERENCES company (id),
    CONSTRAINT fk_employee_address_employee_company FOREIGN KEY (employee_id, company_id) REFERENCES employee (id, company_id)
);

CREATE INDEX ix_employee_address_employee ON employee_address (company_id, employee_id, primary_address DESC);

CREATE TABLE employee_payment_account (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    employee_id UUID NOT NULL,
    bank_name VARCHAR(150),
    bank_code VARCHAR(20),
    branch VARCHAR(30),
    account_type VARCHAR(30),
    account_number_ciphertext TEXT,
    account_number_masked VARCHAR(40),
    pix_key_type VARCHAR(30),
    pix_key_ciphertext TEXT,
    pix_key_masked VARCHAR(120),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_employee_payment_account_company FOREIGN KEY (company_id) REFERENCES company (id),
    CONSTRAINT fk_employee_payment_account_employee_company FOREIGN KEY (employee_id, company_id) REFERENCES employee (id, company_id),
    CONSTRAINT ck_employee_payment_account_details CHECK (
        account_number_ciphertext IS NOT NULL OR pix_key_ciphertext IS NOT NULL
    )
);

CREATE INDEX ix_employee_payment_account_employee ON employee_payment_account (company_id, employee_id, active);
