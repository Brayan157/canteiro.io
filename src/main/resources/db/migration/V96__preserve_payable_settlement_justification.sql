ALTER TABLE payable_settlement_operation
    ALTER CONSTRAINT payable_settlement_operation_change_request_id_company_id_fkey
    DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE payable_settlement
    ADD COLUMN justification VARCHAR(1000),
    ADD CONSTRAINT ck_payable_settlement_reversal_justification
        CHECK (reversal_of_id IS NULL OR nullif(btrim(justification), '') IS NOT NULL) NOT VALID;
