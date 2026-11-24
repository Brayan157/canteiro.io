ALTER TABLE work_diary_occurrence
    ADD COLUMN starts_at TIMESTAMPTZ,
    ADD COLUMN ends_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_diary_occurrence_timing CHECK (
        ends_at IS NULL OR (starts_at IS NOT NULL AND ends_at > starts_at)
    );

ALTER TABLE work_diary_photo
    ADD COLUMN activity_id UUID,
    ADD COLUMN occurrence_id UUID,
    ADD CONSTRAINT ck_diary_photo_single_item CHECK (activity_id IS NULL OR occurrence_id IS NULL),
    ADD CONSTRAINT fk_diary_photo_activity FOREIGN KEY (diary_id, revision, activity_id, company_id, work_id)
        REFERENCES work_diary_activity (diary_id, revision, activity_id, company_id, work_id),
    ADD CONSTRAINT fk_diary_photo_occurrence FOREIGN KEY (diary_id, revision, occurrence_id)
        REFERENCES work_diary_occurrence (diary_id, revision, occurrence_id);

CREATE INDEX ix_diary_photo_activity ON work_diary_photo (diary_id, revision, activity_id)
    WHERE activity_id IS NOT NULL;
CREATE INDEX ix_diary_photo_occurrence ON work_diary_photo (diary_id, revision, occurrence_id)
    WHERE occurrence_id IS NOT NULL;
