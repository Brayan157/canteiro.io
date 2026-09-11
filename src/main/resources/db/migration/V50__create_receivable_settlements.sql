ALTER TABLE receivable
    ADD CONSTRAINT uk_receivable_id_company_contract UNIQUE (id, company_id, contract_id);

ALTER TABLE receivable
    ADD COLUMN settlement_version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE receivable_settlement (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    receivable_id UUID NOT NULL,
    contract_id UUID NOT NULL,
    received_on DATE NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    payment_method VARCHAR(30) NOT NULL,
    note VARCHAR(1000),
    status VARCHAR(30) NOT NULL DEFAULT 'CONFIRMED',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_receivable_settlement_receivable_company_contract
        FOREIGN KEY (receivable_id, company_id, contract_id)
        REFERENCES receivable (id, company_id, contract_id),
    CONSTRAINT ck_receivable_settlement_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_receivable_settlement_note_not_blank CHECK (
        note IS NULL OR LENGTH(BTRIM(note)) > 0
    ),
    CONSTRAINT ck_receivable_settlement_payment_method CHECK (
        payment_method IN ('CASH', 'PIX', 'BANK_TRANSFER', 'CHECK', 'CREDIT_CARD', 'DEBIT_CARD', 'OTHER')
    ),
    CONSTRAINT ck_receivable_settlement_status CHECK (status IN ('CONFIRMED', 'CANCELLED'))
);

CREATE INDEX ix_receivable_settlement_company_receivable_status
    ON receivable_settlement (company_id, receivable_id, status);
