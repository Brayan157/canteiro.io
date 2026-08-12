CREATE TABLE audit_event (
    id UUID PRIMARY KEY,
    company_id UUID,
    actor_user_id UUID NOT NULL,
    actor_type VARCHAR(32) NOT NULL,
    module VARCHAR(40) NOT NULL,
    action VARCHAR(40) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id UUID,
    before_data JSONB,
    after_data JSONB,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_audit_event_actor_type CHECK (actor_type IN ('COMPANY_USER', 'PLATFORM_USER')),
    CONSTRAINT ck_audit_event_module_not_blank CHECK (LENGTH(BTRIM(module)) > 0),
    CONSTRAINT ck_audit_event_action_not_blank CHECK (LENGTH(BTRIM(action)) > 0),
    CONSTRAINT ck_audit_event_entity_type_not_blank CHECK (LENGTH(BTRIM(entity_type)) > 0),
    CONSTRAINT ck_audit_event_before_data_object CHECK (before_data IS NULL OR JSONB_TYPEOF(before_data) = 'object'),
    CONSTRAINT ck_audit_event_after_data_object CHECK (after_data IS NULL OR JSONB_TYPEOF(after_data) = 'object'),
    CONSTRAINT ck_audit_event_metadata_object CHECK (JSONB_TYPEOF(metadata) = 'object'),
    CONSTRAINT fk_audit_event_company FOREIGN KEY (company_id) REFERENCES company (id),
    CONSTRAINT fk_audit_event_actor_user FOREIGN KEY (actor_user_id) REFERENCES app_user (id)
);

CREATE INDEX ix_audit_event_company_occurred_at ON audit_event (company_id, occurred_at DESC);
CREATE INDEX ix_audit_event_actor_occurred_at ON audit_event (actor_user_id, occurred_at DESC);
CREATE INDEX ix_audit_event_entity ON audit_event (entity_type, entity_id);

CREATE FUNCTION prevent_audit_event_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'audit_event is immutable';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_event_immutable
    BEFORE UPDATE OR DELETE ON audit_event
    FOR EACH ROW
    EXECUTE FUNCTION prevent_audit_event_mutation();
