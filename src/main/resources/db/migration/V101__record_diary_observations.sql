ALTER TABLE work_diary_revision
    ADD COLUMN weather_conditions TEXT[] NOT NULL DEFAULT '{}',
    ADD CONSTRAINT ck_work_diary_weather CHECK (
        cardinality(weather_conditions) <= 3
        AND weather_conditions <@ ARRAY['SUNNY', 'CLOUDY', 'RAIN']::TEXT[]
    );

ALTER TABLE work_diary_activity
    ADD COLUMN starts_at TIMESTAMPTZ,
    ADD COLUMN ends_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_work_diary_activity_timing CHECK (
        ends_at IS NULL OR (starts_at IS NOT NULL AND ends_at > starts_at)
    );

ALTER TABLE work_diary_occurrence
    ADD COLUMN stoppage_starts_at TIMESTAMPTZ,
    ADD COLUMN resumed_at TIMESTAMPTZ,
    ADD COLUMN crew_departed_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_work_diary_stoppage_timing CHECK (
        (resumed_at IS NULL OR (stoppage_starts_at IS NOT NULL AND resumed_at > stoppage_starts_at))
        AND (crew_departed_at IS NULL OR (stoppage_starts_at IS NOT NULL AND crew_departed_at > stoppage_starts_at))
        AND (resumed_at IS NULL OR crew_departed_at IS NULL)
    );

CREATE TABLE work_diary_activity_participant (
    diary_id UUID NOT NULL,
    revision BIGINT NOT NULL,
    activity_id UUID NOT NULL,
    company_id UUID NOT NULL,
    work_id UUID NOT NULL,
    employee_id UUID NOT NULL,
    employment_id UUID NOT NULL,
    assignment_id UUID,
    occasional_reason VARCHAR(500),
    assigned_elsewhere BOOLEAN NOT NULL,
    PRIMARY KEY (diary_id, revision, activity_id, employee_id),
    CONSTRAINT fk_diary_participant_activity FOREIGN KEY (diary_id, revision, activity_id, company_id, work_id)
        REFERENCES work_diary_activity (diary_id, revision, activity_id, company_id, work_id),
    CONSTRAINT fk_diary_participant_employment FOREIGN KEY (employment_id, company_id, employee_id)
        REFERENCES employee_employment (id, company_id, employee_id),
    CONSTRAINT fk_diary_participant_assignment FOREIGN KEY (assignment_id, company_id, work_id, employment_id, employee_id)
        REFERENCES work_assignment (id, company_id, work_id, employment_id, employee_id),
    CONSTRAINT ck_diary_participant_occasional CHECK (
        (assignment_id IS NOT NULL AND occasional_reason IS NULL) OR
        (assignment_id IS NULL AND occasional_reason IS NOT NULL AND length(btrim(occasional_reason)) > 0)
    )
);
