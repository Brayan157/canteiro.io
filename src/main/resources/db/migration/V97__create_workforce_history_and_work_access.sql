CREATE TABLE employee_employment (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    employee_id UUID NOT NULL,
    started_on DATE NOT NULL,
    ended_on DATE,
    recorded_by_user_id UUID NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_employment_employee FOREIGN KEY (employee_id, company_id)
        REFERENCES employee (id, company_id),
    CONSTRAINT fk_employment_actor FOREIGN KEY (recorded_by_user_id, company_id)
        REFERENCES company_user (user_id, company_id),
    CONSTRAINT uk_employment_chain UNIQUE (id, company_id, employee_id),
    CONSTRAINT ck_employment_dates CHECK (ended_on IS NULL OR ended_on >= started_on)
);

CREATE UNIQUE INDEX uk_employment_open ON employee_employment (company_id, employee_id) WHERE ended_on IS NULL;
CREATE INDEX ix_employment_employee_dates ON employee_employment (company_id, employee_id, started_on, ended_on);

CREATE TABLE work_assignment (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    work_id UUID NOT NULL,
    employee_id UUID NOT NULL,
    employment_id UUID NOT NULL,
    started_on DATE NOT NULL,
    ended_on DATE,
    recorded_by_user_id UUID NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_work_assignment_work FOREIGN KEY (work_id, company_id) REFERENCES obra (id, company_id),
    CONSTRAINT fk_work_assignment_employment FOREIGN KEY (employment_id, company_id, employee_id)
        REFERENCES employee_employment (id, company_id, employee_id),
    CONSTRAINT fk_work_assignment_actor FOREIGN KEY (recorded_by_user_id, company_id)
        REFERENCES company_user (user_id, company_id),
    CONSTRAINT uk_work_assignment_chain UNIQUE (id, company_id, work_id, employment_id, employee_id),
    CONSTRAINT ck_work_assignment_dates CHECK (ended_on IS NULL OR ended_on >= started_on)
);

CREATE INDEX ix_work_assignment_work_date ON work_assignment (company_id, work_id, started_on, ended_on);
CREATE INDEX ix_work_assignment_employee ON work_assignment (company_id, employee_id, started_on, ended_on);

CREATE TABLE work_access_grant (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    work_id UUID NOT NULL,
    user_id UUID NOT NULL,
    operation VARCHAR(32) NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    granted_by_user_id UUID NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_work_grant_work FOREIGN KEY (work_id, company_id) REFERENCES obra (id, company_id),
    CONSTRAINT fk_work_grant_user FOREIGN KEY (user_id, company_id) REFERENCES company_user (user_id, company_id),
    CONSTRAINT fk_work_grant_actor FOREIGN KEY (granted_by_user_id, company_id)
        REFERENCES company_user (user_id, company_id),
    CONSTRAINT ck_work_grant_validity CHECK (ends_at IS NULL OR ends_at > starts_at),
    CONSTRAINT ck_work_grant_operation CHECK (operation IN (
        'READ', 'CREATE', 'UPDATE', 'CLOSE', 'REOPEN', 'APPROVE', 'REJECT', 'REGISTER_OCCASIONAL'
    ))
);

CREATE INDEX ix_work_grant_scope ON work_access_grant (company_id, work_id, user_id, operation)
    WHERE revoked_at IS NULL;

INSERT INTO permission (id, module, action, active) VALUES
    ('00000000-0000-0000-0000-000000009701', 'WORKFORCE', 'MANAGE_USERS', TRUE),
    ('00000000-0000-0000-0000-000000009702', 'WORKFORCE', 'MANAGE_ROLES', TRUE)
ON CONFLICT (module, action) DO NOTHING;
