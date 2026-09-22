CREATE TABLE employee_registration_sequence (
    company_id UUID PRIMARY KEY,
    next_number BIGINT NOT NULL,
    CONSTRAINT fk_employee_registration_sequence_company FOREIGN KEY (company_id) REFERENCES company (id),
    CONSTRAINT ck_employee_registration_sequence_next_number CHECK (next_number > 0)
);
