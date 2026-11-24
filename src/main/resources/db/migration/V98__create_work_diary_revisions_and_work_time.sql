CREATE TABLE work_diary_zone (
    work_id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    zone_id VARCHAR(100) NOT NULL,
    CONSTRAINT fk_work_diary_zone_work FOREIGN KEY (work_id, company_id) REFERENCES obra (id, company_id)
);

CREATE TABLE work_diary (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    work_id UUID NOT NULL,
    performed_on DATE NOT NULL,
    zone_id VARCHAR(100) NOT NULL,
    day_starts_at TIMESTAMPTZ NOT NULL,
    day_ends_at TIMESTAMPTZ NOT NULL,
    current_revision BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_work_diary_work FOREIGN KEY (work_id, company_id) REFERENCES obra (id, company_id),
    CONSTRAINT uk_work_diary_day UNIQUE (company_id, work_id, performed_on),
    CONSTRAINT uk_work_diary_scope UNIQUE (id, company_id, work_id),
    CONSTRAINT ck_work_diary_day CHECK (day_starts_at < day_ends_at AND current_revision >= 0)
);

CREATE TABLE work_diary_revision (
    diary_id UUID NOT NULL,
    revision BIGINT NOT NULL,
    company_id UUID NOT NULL,
    work_id UUID NOT NULL,
    closed BOOLEAN NOT NULL,
    no_execution_reason VARCHAR(500),
    reason VARCHAR(500),
    actor_user_id UUID NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (diary_id, revision),
    CONSTRAINT uk_work_diary_revision_scope UNIQUE (diary_id, revision, company_id, work_id),
    CONSTRAINT fk_work_diary_revision_diary FOREIGN KEY (diary_id, company_id, work_id)
        REFERENCES work_diary (id, company_id, work_id),
    CONSTRAINT fk_work_diary_revision_actor FOREIGN KEY (actor_user_id, company_id)
        REFERENCES company_user (user_id, company_id)
);

ALTER TABLE work_diary ADD CONSTRAINT fk_work_diary_current_revision
    FOREIGN KEY (id, current_revision) REFERENCES work_diary_revision (diary_id, revision)
    DEFERRABLE INITIALLY DEFERRED;

CREATE TABLE work_diary_activity (
    diary_id UUID NOT NULL,
    revision BIGINT NOT NULL,
    activity_id UUID NOT NULL,
    company_id UUID NOT NULL,
    work_id UUID NOT NULL,
    contract_id UUID,
    contract_service_id UUID,
    description VARCHAR(1000) NOT NULL,
    shift_name VARCHAR(120),
    front_name VARCHAR(120),
    PRIMARY KEY (diary_id, revision, activity_id),
    CONSTRAINT uk_work_diary_activity_scope UNIQUE (diary_id, revision, activity_id, company_id, work_id),
    CONSTRAINT fk_work_diary_activity_revision FOREIGN KEY (diary_id, revision, company_id, work_id)
        REFERENCES work_diary_revision (diary_id, revision, company_id, work_id),
    CONSTRAINT fk_work_diary_activity_contract FOREIGN KEY (contract_id, company_id, work_id)
        REFERENCES contract (id, company_id, work_id),
    CONSTRAINT fk_work_diary_activity_service FOREIGN KEY (contract_service_id, company_id, contract_id)
        REFERENCES contract_service (id, company_id, contract_id),
    CONSTRAINT ck_work_diary_activity_link CHECK ((contract_id IS NULL) = (contract_service_id IS NULL)),
    CONSTRAINT ck_work_diary_activity_description CHECK (length(btrim(description)) > 0)
);

CREATE TABLE work_time_entry (
    diary_id UUID NOT NULL,
    revision BIGINT NOT NULL,
    activity_id UUID NOT NULL,
    entry_id UUID NOT NULL,
    company_id UUID NOT NULL,
    work_id UUID NOT NULL,
    employee_id UUID NOT NULL,
    employment_id UUID NOT NULL,
    assignment_id UUID,
    occasional_reason VARCHAR(500),
    assigned_elsewhere BOOLEAN NOT NULL,
    minutes INTEGER NOT NULL,
    starts_at TIMESTAMPTZ,
    ends_at TIMESTAMPTZ,
    PRIMARY KEY (diary_id, revision, entry_id),
    CONSTRAINT fk_work_time_activity FOREIGN KEY (diary_id, revision, activity_id, company_id, work_id)
        REFERENCES work_diary_activity (diary_id, revision, activity_id, company_id, work_id),
    CONSTRAINT fk_work_time_employment FOREIGN KEY (employment_id, company_id, employee_id)
        REFERENCES employee_employment (id, company_id, employee_id),
    CONSTRAINT fk_work_time_assignment FOREIGN KEY (assignment_id, company_id, work_id, employment_id, employee_id)
        REFERENCES work_assignment (id, company_id, work_id, employment_id, employee_id),
    CONSTRAINT ck_work_time_minutes CHECK (minutes > 0),
    CONSTRAINT ck_work_time_interval CHECK (
        (starts_at IS NULL AND ends_at IS NULL) OR
        (starts_at IS NOT NULL AND ends_at IS NOT NULL AND ends_at = starts_at + minutes * INTERVAL '1 minute')
    ),
    CONSTRAINT ck_work_time_occasional CHECK (
        (assignment_id IS NOT NULL AND occasional_reason IS NULL) OR
        (assignment_id IS NULL AND occasional_reason IS NOT NULL AND length(btrim(occasional_reason)) > 0)
    )
);

CREATE INDEX ix_work_time_employee ON work_time_entry (company_id, employee_id, starts_at, ends_at);

CREATE TABLE work_diary_occurrence (
    diary_id UUID NOT NULL,
    revision BIGINT NOT NULL,
    occurrence_id UUID NOT NULL,
    activity_id UUID,
    company_id UUID NOT NULL,
    work_id UUID NOT NULL,
    description VARCHAR(1000) NOT NULL,
    delay_reason VARCHAR(500),
    PRIMARY KEY (diary_id, revision, occurrence_id),
    CONSTRAINT fk_work_diary_occurrence_revision FOREIGN KEY (diary_id, revision, company_id, work_id)
        REFERENCES work_diary_revision (diary_id, revision, company_id, work_id),
    CONSTRAINT fk_work_diary_occurrence_activity FOREIGN KEY (diary_id, revision, activity_id, company_id, work_id)
        REFERENCES work_diary_activity (diary_id, revision, activity_id, company_id, work_id),
    CONSTRAINT ck_work_diary_occurrence_description CHECK (length(btrim(description)) > 0)
);

CREATE TABLE work_diary_operation (
    company_id UUID NOT NULL,
    work_id UUID NOT NULL,
    operation_id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    entity_id UUID NOT NULL,
    applied_revision BIGINT,
    change_request_id UUID,
    PRIMARY KEY (company_id, work_id, operation_id),
    CONSTRAINT fk_work_diary_operation_work FOREIGN KEY (work_id, company_id) REFERENCES obra (id, company_id),
    CONSTRAINT fk_work_diary_operation_actor FOREIGN KEY (actor_user_id, company_id)
        REFERENCES company_user (user_id, company_id),
    CONSTRAINT fk_work_diary_operation_request FOREIGN KEY (change_request_id, company_id)
        REFERENCES change_request (id, company_id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT fk_work_diary_operation_applied FOREIGN KEY (entity_id, applied_revision, company_id, work_id)
        REFERENCES work_diary_revision (diary_id, revision, company_id, work_id),
    CONSTRAINT ck_work_diary_operation_result CHECK (applied_revision IS NOT NULL OR change_request_id IS NOT NULL)
);

CREATE INDEX ix_work_diary_operation_entity ON work_diary_operation (company_id, work_id, entity_id);

CREATE VIEW work_time_effective AS
SELECT e.*, d.performed_on, a.contract_service_id
FROM work_time_entry e
JOIN work_diary d ON d.id = e.diary_id AND d.current_revision = e.revision
JOIN work_diary_activity a ON a.diary_id = e.diary_id AND a.revision = e.revision AND a.activity_id = e.activity_id;

CREATE FUNCTION prevent_work_diary_history_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Work diary history is immutable';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_work_diary_revision_immutable BEFORE UPDATE OR DELETE ON work_diary_revision
    FOR EACH ROW EXECUTE FUNCTION prevent_work_diary_history_mutation();
CREATE TRIGGER trg_work_diary_activity_immutable BEFORE UPDATE OR DELETE ON work_diary_activity
    FOR EACH ROW EXECUTE FUNCTION prevent_work_diary_history_mutation();
CREATE TRIGGER trg_work_diary_occurrence_immutable BEFORE UPDATE OR DELETE ON work_diary_occurrence
    FOR EACH ROW EXECUTE FUNCTION prevent_work_diary_history_mutation();
CREATE TRIGGER trg_work_time_entry_immutable BEFORE UPDATE OR DELETE ON work_time_entry
    FOR EACH ROW EXECUTE FUNCTION prevent_work_diary_history_mutation();

INSERT INTO permission (id, module, action, active) VALUES
    ('00000000-0000-0000-0000-000000009801', 'WORK_DIARY', 'READ', TRUE),
    ('00000000-0000-0000-0000-000000009802', 'WORK_DIARY', 'CREATE_DIRECT', TRUE),
    ('00000000-0000-0000-0000-000000009803', 'WORK_DIARY', 'UPDATE_DIRECT', TRUE),
    ('00000000-0000-0000-0000-000000009804', 'WORK_DIARY', 'REQUEST_CREATE', TRUE),
    ('00000000-0000-0000-0000-000000009805', 'WORK_DIARY', 'REQUEST_UPDATE', TRUE),
    ('00000000-0000-0000-0000-000000009806', 'WORK_DIARY', 'APPROVE', TRUE),
    ('00000000-0000-0000-0000-000000009807', 'WORK_DIARY', 'REJECT', TRUE)
ON CONFLICT (module, action) DO NOTHING;
