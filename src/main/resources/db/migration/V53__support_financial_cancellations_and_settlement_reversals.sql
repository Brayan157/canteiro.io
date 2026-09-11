ALTER TABLE invoice_item
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'REGISTERED';

ALTER TABLE invoice_item
    ADD CONSTRAINT ck_invoice_item_status CHECK (status IN ('REGISTERED', 'CANCELLED'));

CREATE INDEX ix_invoice_item_company_invoice_status
    ON invoice_item (company_id, invoice_id, status);

ALTER TABLE receivable_settlement
    ADD COLUMN entry_type VARCHAR(20) NOT NULL DEFAULT 'SETTLEMENT',
    ADD COLUMN reversal_of_settlement_id UUID;

ALTER TABLE receivable_settlement
    DROP CONSTRAINT ck_receivable_settlement_amount_positive;

ALTER TABLE receivable_settlement
    ADD CONSTRAINT uk_receivable_settlement_id_company_receivable_contract
        UNIQUE (id, company_id, receivable_id, contract_id);

ALTER TABLE receivable_settlement
    ADD CONSTRAINT fk_receivable_settlement_reversal_origin
        FOREIGN KEY (reversal_of_settlement_id, company_id, receivable_id, contract_id)
        REFERENCES receivable_settlement (id, company_id, receivable_id, contract_id),
    ADD CONSTRAINT uk_receivable_settlement_reversal_origin UNIQUE (reversal_of_settlement_id),
    ADD CONSTRAINT ck_receivable_settlement_entry_type CHECK (entry_type IN ('SETTLEMENT', 'REVERSAL')),
    ADD CONSTRAINT ck_receivable_settlement_entry_sign CHECK (
        (entry_type = 'SETTLEMENT' AND amount > 0 AND reversal_of_settlement_id IS NULL)
        OR (entry_type = 'REVERSAL' AND amount < 0 AND reversal_of_settlement_id IS NOT NULL)
    );
