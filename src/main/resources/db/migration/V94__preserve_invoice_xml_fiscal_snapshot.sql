ALTER TABLE invoice_attachment ADD CONSTRAINT uk_invoice_attachment_snapshot_source
    UNIQUE (id, company_id, invoice_id, contract_id, attachment_type);

CREATE TABLE invoice_xml_fiscal_snapshot (
    invoice_id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    contract_id UUID NOT NULL,
    attachment_id UUID NOT NULL,
    attachment_type VARCHAR(10) NOT NULL DEFAULT 'XML' CHECK (attachment_type = 'XML'),
    access_key_sha256 VARCHAR(64) NOT NULL CHECK (access_key_sha256 ~ '^[0-9a-f]{64}$'),
    source_profile VARCHAR(30) NOT NULL CHECK (source_profile = 'NFSE_NACIONAL_1_01'),
    currency VARCHAR(3) NOT NULL CHECK (currency = 'BRL'),
    facts JSONB NOT NULL CHECK (jsonb_typeof(facts) = 'object'),
    confirmed_by UUID NOT NULL,
    confirmed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_invoice_xml_snapshot_fiscal_identity UNIQUE (company_id, access_key_sha256),
    CONSTRAINT fk_invoice_xml_snapshot_source FOREIGN KEY
        (attachment_id, company_id, invoice_id, contract_id, attachment_type)
        REFERENCES invoice_attachment (id, company_id, invoice_id, contract_id, attachment_type),
    CONSTRAINT fk_invoice_xml_snapshot_actor FOREIGN KEY (confirmed_by, company_id)
        REFERENCES company_user (user_id, company_id)
);

CREATE FUNCTION prevent_invoice_xml_snapshot_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Invoice XML fiscal snapshots are immutable' USING ERRCODE = '23514';
END;
$$;

CREATE TRIGGER trg_invoice_xml_snapshot_immutable
    BEFORE UPDATE OR DELETE ON invoice_xml_fiscal_snapshot
    FOR EACH ROW EXECUTE FUNCTION prevent_invoice_xml_snapshot_mutation();
