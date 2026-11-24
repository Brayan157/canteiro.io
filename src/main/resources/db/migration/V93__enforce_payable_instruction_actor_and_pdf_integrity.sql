-- Preserve the applied V92 checksum and validate existing revisions without rewriting them.
ALTER TABLE payable_payment_instruction
    ADD CONSTRAINT fk_payable_instruction_actor_tenant
        FOREIGN KEY (created_by, company_id) REFERENCES company_user(user_id, company_id),
    ADD CONSTRAINT ck_payable_instruction_pdf_metadata_complete
        CHECK (pdf_storage_key IS NULL OR (pdf_sha256 IS NOT NULL AND pdf_size IS NOT NULL));
