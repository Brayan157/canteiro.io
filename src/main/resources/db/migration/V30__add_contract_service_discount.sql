ALTER TABLE contract_service
    ADD COLUMN discount_type VARCHAR(20),
    ADD COLUMN discount_value NUMERIC(19, 4),
    ADD COLUMN discount_amount NUMERIC(19, 2) NOT NULL DEFAULT 0,
    ADD COLUMN net_amount NUMERIC(19, 2) NOT NULL DEFAULT 0;

UPDATE contract_service
SET net_amount = gross_amount
WHERE net_amount = 0;

ALTER TABLE contract_service
    ADD CONSTRAINT ck_contract_service_discount_type CHECK (
        discount_type IS NULL OR discount_type IN ('FIXED', 'PERCENTAGE')
    ),
    ADD CONSTRAINT ck_contract_service_discount_value CHECK (
        (discount_type IS NULL AND discount_value IS NULL)
        OR (discount_type IS NOT NULL AND discount_value IS NOT NULL AND discount_value > 0)
    ),
    ADD CONSTRAINT ck_contract_service_discount_amount CHECK (
        discount_amount >= 0 AND discount_amount <= gross_amount
    ),
    ADD CONSTRAINT ck_contract_service_net_amount CHECK (
        net_amount = gross_amount - discount_amount AND net_amount >= 0
    );
