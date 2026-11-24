CREATE TABLE work_diary_photo (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    work_id UUID NOT NULL,
    diary_id UUID NOT NULL,
    revision BIGINT NOT NULL,
    operation_id UUID NOT NULL,
    content_type VARCHAR(16) NOT NULL,
    size_bytes INTEGER NOT NULL,
    sha256 CHAR(64) NOT NULL,
    uploaded_by UUID NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL,
    withdrawn_by UUID,
    withdrawn_at TIMESTAMPTZ,
    withdrawal_reason VARCHAR(500),
    CONSTRAINT uk_diary_photo_operation UNIQUE (diary_id, operation_id),
    CONSTRAINT fk_diary_photo_revision FOREIGN KEY (diary_id, revision, company_id, work_id)
        REFERENCES work_diary_revision (diary_id, revision, company_id, work_id),
    CONSTRAINT fk_diary_photo_uploader FOREIGN KEY (uploaded_by, company_id)
        REFERENCES company_user (user_id, company_id),
    CONSTRAINT fk_diary_photo_withdrawer FOREIGN KEY (withdrawn_by, company_id)
        REFERENCES company_user (user_id, company_id),
    CONSTRAINT ck_diary_photo_format CHECK (content_type IN ('image/jpeg', 'image/png')),
    CONSTRAINT ck_diary_photo_size CHECK (size_bytes BETWEEN 1 AND 8388608),
    CONSTRAINT ck_diary_photo_digest CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_diary_photo_withdrawal CHECK (
        (withdrawn_at IS NULL AND withdrawn_by IS NULL AND withdrawal_reason IS NULL)
        OR (withdrawn_at IS NOT NULL AND withdrawn_by IS NOT NULL AND withdrawal_reason IS NOT NULL
            AND length(btrim(withdrawal_reason)) > 0 AND withdrawn_at >= uploaded_at)
    )
);

CREATE INDEX ix_diary_photo_day ON work_diary_photo (company_id, work_id, diary_id, uploaded_at, id);
