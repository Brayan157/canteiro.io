CREATE TABLE invoice_attachment (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    invoice_id UUID NOT NULL,
    contract_id UUID NOT NULL,
    attachment_type VARCHAR(10) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    content_size BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    uploaded_by_user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_invoice_attachment_invoice_company_contract
        FOREIGN KEY (invoice_id, company_id, contract_id)
        REFERENCES invoice (id, company_id, contract_id),
    CONSTRAINT uk_invoice_attachment_invoice_hash UNIQUE (invoice_id, company_id, sha256),
    CONSTRAINT ck_invoice_attachment_type CHECK (attachment_type IN ('PDF', 'XML')),
    CONSTRAINT ck_invoice_attachment_filename_not_blank CHECK (LENGTH(BTRIM(original_filename)) > 0),
    CONSTRAINT ck_invoice_attachment_content_type_not_blank CHECK (LENGTH(BTRIM(content_type)) > 0),
    CONSTRAINT ck_invoice_attachment_content_size_positive CHECK (content_size > 0),
    CONSTRAINT ck_invoice_attachment_sha256 CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_invoice_attachment_storage_key_not_blank CHECK (LENGTH(BTRIM(storage_key)) > 0)
);

CREATE INDEX ix_invoice_attachment_company_invoice ON invoice_attachment (company_id, invoice_id, created_at);
