ALTER TABLE contract_discount
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN contract_discount.active IS
    'False means the contract-level discount was reversed; the row remains for audit history.';
