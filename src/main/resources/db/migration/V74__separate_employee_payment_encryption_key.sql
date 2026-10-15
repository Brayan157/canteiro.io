ALTER TABLE employee_payment_account
    ADD COLUMN encryption_key_version VARCHAR(30) NOT NULL DEFAULT 'LEGACY_JWT';

ALTER TABLE employee_payment_account
    ADD CONSTRAINT ck_employee_payment_account_encryption_key_version
        CHECK (encryption_key_version IN ('LEGACY_JWT', 'PAYMENT_DATA_KEY'));
