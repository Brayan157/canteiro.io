ALTER TABLE subscription
    ADD COLUMN trial_started_on DATE,
    ADD COLUMN trial_ends_on DATE;

ALTER TABLE subscription
    DROP CONSTRAINT ck_subscription_status,
    ADD CONSTRAINT ck_subscription_status CHECK (
        status IN ('PENDING_ACTIVATION', 'TRIAL', 'AWAITING_PAYMENT')
    ),
    ADD CONSTRAINT ck_subscription_trial_dates CHECK (
        (status = 'PENDING_ACTIVATION' AND trial_started_on IS NULL AND trial_ends_on IS NULL)
        OR (
            status IN ('TRIAL', 'AWAITING_PAYMENT')
            AND trial_started_on IS NOT NULL
            AND trial_ends_on = trial_started_on + 30
        )
    );
