CREATE TABLE change_request (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    requester_user_id UUID NOT NULL,
    module VARCHAR(40) NOT NULL,
    operation VARCHAR(24) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id UUID,
    entity_version BIGINT NOT NULL,
    revision INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(24) NOT NULL,
    before_data JSONB,
    proposed_data JSONB NOT NULL,
    justification VARCHAR(1000),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_change_request_operation CHECK (operation IN ('CREATE', 'UPDATE', 'CANCEL')),
    CONSTRAINT ck_change_request_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT ck_change_request_entity_version CHECK (entity_version >= 0),
    CONSTRAINT ck_change_request_revision CHECK (revision >= 1),
    CONSTRAINT ck_change_request_existing_entity CHECK (operation = 'CREATE' OR entity_id IS NOT NULL),
    CONSTRAINT ck_change_request_module_not_blank CHECK (LENGTH(BTRIM(module)) > 0),
    CONSTRAINT ck_change_request_entity_type_not_blank CHECK (LENGTH(BTRIM(entity_type)) > 0),
    CONSTRAINT ck_change_request_before_data_object CHECK (before_data IS NULL OR JSONB_TYPEOF(before_data) = 'object'),
    CONSTRAINT ck_change_request_proposed_data_object CHECK (JSONB_TYPEOF(proposed_data) = 'object'),
    CONSTRAINT ck_change_request_justification_not_blank CHECK (justification IS NULL OR LENGTH(BTRIM(justification)) > 0),
    CONSTRAINT fk_change_request_company FOREIGN KEY (company_id) REFERENCES company (id),
    CONSTRAINT fk_change_request_requester_company_user FOREIGN KEY (requester_user_id, company_id)
        REFERENCES company_user (user_id, company_id)
);

CREATE INDEX ix_change_request_company_status_created_at ON change_request (company_id, status, created_at DESC);
CREATE INDEX ix_change_request_entity ON change_request (company_id, entity_type, entity_id);
CREATE INDEX ix_change_request_requester ON change_request (requester_user_id, created_at DESC);
