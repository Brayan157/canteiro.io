ALTER TABLE app_user
    ADD COLUMN password_hash VARCHAR(255),
    ADD COLUMN password_changed_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN activated_at TIMESTAMP WITH TIME ZONE,
    ADD CONSTRAINT ck_app_user_password CHECK (
        (password_hash IS NULL AND password_changed_at IS NULL)
        OR (password_hash IS NOT NULL AND password_changed_at IS NOT NULL)
    ),
    ADD CONSTRAINT ck_app_user_active_password CHECK (
        status = 'PENDING_ACTIVATION' OR password_hash IS NOT NULL
    );

CREATE TABLE account_activation_token (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_account_activation_token_user UNIQUE (user_id),
    CONSTRAINT uk_account_activation_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_account_activation_token_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);

CREATE INDEX ix_account_activation_token_expires_at ON account_activation_token (expires_at);
