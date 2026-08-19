ALTER TABLE measurement_version
    ADD CONSTRAINT uk_measurement_version_id_company_measurement UNIQUE (id, company_id, measurement_id);

CREATE TABLE measurement_document (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    measurement_id UUID NOT NULL,
    measurement_version_id UUID NOT NULL,
    document_type VARCHAR(20) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    content_size BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    uploaded_by_user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_measurement_document_version_company_measurement
        FOREIGN KEY (measurement_version_id, company_id, measurement_id)
        REFERENCES measurement_version (id, company_id, measurement_id),
    CONSTRAINT fk_measurement_document_uploaded_by FOREIGN KEY (uploaded_by_user_id) REFERENCES app_user (id),
    CONSTRAINT ck_measurement_document_type CHECK (document_type IN ('EVIDENCE', 'SPREADSHEET')),
    CONSTRAINT ck_measurement_document_filename_not_blank CHECK (LENGTH(BTRIM(original_filename)) > 0),
    CONSTRAINT ck_measurement_document_content_size_positive CHECK (content_size > 0),
    CONSTRAINT ck_measurement_document_sha256 CHECK (sha256 ~ '^[a-f0-9]{64}$'),
    CONSTRAINT uk_measurement_document_storage_key UNIQUE (storage_key)
);

CREATE INDEX ix_measurement_document_company_version ON measurement_document (company_id, measurement_version_id);
