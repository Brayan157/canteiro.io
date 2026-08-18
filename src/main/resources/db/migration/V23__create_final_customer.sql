CREATE TABLE final_customer (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    customer_type VARCHAR(20) NOT NULL,
    name VARCHAR(255) NOT NULL,
    document VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_final_customer_company FOREIGN KEY (company_id) REFERENCES company (id),
    CONSTRAINT ck_final_customer_type CHECK (customer_type IN ('INDIVIDUAL', 'LEGAL')),
    CONSTRAINT ck_final_customer_name_not_blank CHECK (LENGTH(BTRIM(name)) > 0),
    CONSTRAINT ck_final_customer_document_format CHECK (document ~ '^[0-9]{11}$|^[0-9]{14}$'),
    CONSTRAINT ck_final_customer_document_matches_type CHECK (
        (customer_type = 'INDIVIDUAL' AND LENGTH(document) = 11)
        OR (customer_type = 'LEGAL' AND LENGTH(document) = 14)
    ),
    CONSTRAINT uk_final_customer_company_document UNIQUE (company_id, document)
);

CREATE INDEX ix_final_customer_company_active ON final_customer (company_id, active);
