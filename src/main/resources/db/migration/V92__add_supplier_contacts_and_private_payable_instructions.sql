-- Additive only. Does not change balances, payments, scope, currency or existing titles.
ALTER TABLE supplier ADD COLUMN contact_name varchar(120);
ALTER TABLE supplier ADD COLUMN contact_phone varchar(40);
ALTER TABLE supplier ADD COLUMN contact_email varchar(254);

CREATE TABLE payable_payment_instruction (
    id uuid PRIMARY KEY,
    company_id uuid NOT NULL REFERENCES company(id),
    payable_id uuid NOT NULL,
    version bigint NOT NULL CHECK(version > 0),
    type varchar(20) NOT NULL CHECK(type IN ('BOLETO','PIX','BANK_TRANSFER')),
    ciphertext text NOT NULL,
    pdf_storage_key varchar(500),
    pdf_sha256 varchar(64),
    pdf_size bigint,
    created_by uuid NOT NULL REFERENCES app_user(id),
    created_at timestamptz NOT NULL,
    CONSTRAINT fk_payable_instruction_tenant FOREIGN KEY(payable_id,company_id) REFERENCES payable(id,company_id),
    CONSTRAINT uq_payable_instruction_version UNIQUE(company_id,payable_id,version),
    CONSTRAINT ck_payable_instruction_pdf CHECK(
        (pdf_storage_key IS NULL AND pdf_sha256 IS NULL AND pdf_size IS NULL) OR
        (type='BOLETO' AND pdf_storage_key IS NOT NULL AND pdf_sha256 ~ '^[a-f0-9]{64}$' AND pdf_size BETWEEN 1 AND 10485760))
);
CREATE FUNCTION protect_payable_payment_instruction() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Payable payment instruction history is immutable' USING ERRCODE='23514';
END; $$;
CREATE TRIGGER protect_payable_payment_instruction BEFORE UPDATE OR DELETE ON payable_payment_instruction
    FOR EACH ROW EXECUTE FUNCTION protect_payable_payment_instruction();

INSERT INTO permission(id,module,action,active)
VALUES ('00000000-0000-0000-0000-000000001814','PAYABLES','VIEW_PAYMENT_DETAILS',true)
ON CONFLICT(module,action) DO NOTHING;
INSERT INTO role_permission(id,role_id,permission_id,active)
SELECT md5(r.id::text || p.id::text)::uuid,r.id,p.id,true
FROM access_role r JOIN permission p ON p.module='PAYABLES' AND p.action='VIEW_PAYMENT_DETAILS'
WHERE r.name='Company Administrator'
ON CONFLICT(role_id,permission_id) DO NOTHING;
