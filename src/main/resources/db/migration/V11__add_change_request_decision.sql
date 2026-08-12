ALTER TABLE change_request
    ADD COLUMN decided_by_user_id UUID,
    ADD COLUMN decision_reason VARCHAR(1000),
    ADD COLUMN decided_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE change_request
    ADD CONSTRAINT ck_change_request_decision_reason_not_blank
        CHECK (decision_reason IS NULL OR LENGTH(BTRIM(decision_reason)) > 0),
    ADD CONSTRAINT ck_change_request_decision_fields
        CHECK (
            (status = 'PENDING' AND decided_by_user_id IS NULL AND decision_reason IS NULL AND decided_at IS NULL)
            OR (status = 'APPROVED' AND decided_by_user_id IS NOT NULL AND decided_at IS NOT NULL)
            OR (status = 'REJECTED' AND decided_by_user_id IS NOT NULL AND decision_reason IS NOT NULL AND decided_at IS NOT NULL)
            OR status = 'CANCELLED'
        ),
    ADD CONSTRAINT fk_change_request_decider_company_user FOREIGN KEY (decided_by_user_id, company_id)
        REFERENCES company_user (user_id, company_id);

CREATE INDEX ix_change_request_decider ON change_request (decided_by_user_id, decided_at DESC);
