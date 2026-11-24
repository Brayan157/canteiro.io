CREATE TABLE work_diary_plan (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    work_id UUID NOT NULL,
    planned_on DATE NOT NULL,
    actor_user_id UUID NOT NULL,
    operation_id UUID NOT NULL,
    payload TEXT NOT NULL,
    zone_id VARCHAR(100) NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL,
    change_request_id UUID,
    official_version BIGINT,
    applied_at TIMESTAMPTZ,
    CONSTRAINT fk_diary_plan_work FOREIGN KEY (work_id, company_id) REFERENCES obra (id, company_id),
    CONSTRAINT fk_diary_plan_actor FOREIGN KEY (actor_user_id, company_id) REFERENCES company_user (user_id, company_id),
    CONSTRAINT fk_diary_plan_request FOREIGN KEY (change_request_id, company_id)
        REFERENCES change_request (id, company_id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT uk_diary_plan_operation UNIQUE (company_id, work_id, operation_id),
    CONSTRAINT uk_diary_plan_version UNIQUE (company_id, work_id, planned_on, official_version),
    CONSTRAINT ck_diary_plan_application CHECK (
        (official_version IS NULL AND applied_at IS NULL AND change_request_id IS NOT NULL) OR
        (official_version IS NOT NULL AND official_version > 0 AND applied_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uk_work_diary_plan_request ON work_diary_plan (change_request_id) WHERE change_request_id IS NOT NULL;

ALTER TABLE work_access_grant DROP CONSTRAINT ck_work_grant_operation;
ALTER TABLE work_access_grant ADD CONSTRAINT ck_work_grant_operation CHECK (operation IN (
    'READ', 'CREATE', 'UPDATE', 'CLOSE', 'REOPEN', 'APPROVE', 'REJECT', 'REGISTER_OCCASIONAL', 'PLAN'
));

CREATE FUNCTION prevent_official_work_diary_plan_mutation() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' OR OLD.official_version IS NOT NULL THEN
        RAISE EXCEPTION 'Work diary plan history is immutable';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_work_diary_plan_history BEFORE UPDATE OR DELETE ON work_diary_plan
    FOR EACH ROW EXECUTE FUNCTION prevent_official_work_diary_plan_mutation();
