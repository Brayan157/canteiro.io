ALTER TABLE contract_service
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    ADD COLUMN quantity NUMERIC(19, 4) NOT NULL DEFAULT 1,
    ADD COLUMN unit_price NUMERIC(19, 2) NOT NULL DEFAULT 0,
    ADD COLUMN gross_amount NUMERIC(19, 2) NOT NULL DEFAULT 0;

ALTER TABLE contract_service
    ADD CONSTRAINT ck_contract_service_status CHECK (status IN ('DRAFT', 'ACTIVE', 'COMPLETED', 'CANCELLED')),
    ADD CONSTRAINT ck_contract_service_quantity_positive CHECK (quantity > 0),
    ADD CONSTRAINT ck_contract_service_unit_price_non_negative CHECK (unit_price >= 0),
    ADD CONSTRAINT ck_contract_service_gross_amount_non_negative CHECK (gross_amount >= 0);
