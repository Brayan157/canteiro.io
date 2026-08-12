CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    user_type VARCHAR(20) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_app_user_type CHECK (user_type IN ('COMPANY', 'PLATFORM')),
    CONSTRAINT ck_app_user_status CHECK (status IN ('PENDING_ACTIVATION', 'ACTIVE', 'LOCKED', 'INACTIVE')),
    CONSTRAINT uk_app_user_id_type UNIQUE (id, user_type)
);

CREATE UNIQUE INDEX uk_app_user_email_normalized ON app_user (LOWER(email));

CREATE TABLE company_user (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    company_id UUID NOT NULL,
    user_type VARCHAR(20) NOT NULL DEFAULT 'COMPANY',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_company_user_type CHECK (user_type = 'COMPANY'),
    CONSTRAINT uk_company_user_user UNIQUE (user_id),
    CONSTRAINT fk_company_user_company FOREIGN KEY (company_id) REFERENCES company (id),
    CONSTRAINT fk_company_user_user FOREIGN KEY (user_id, user_type) REFERENCES app_user (id, user_type)
);

CREATE INDEX ix_company_user_company_id ON company_user (company_id);

CREATE TABLE platform_user (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    global_role VARCHAR(32) NOT NULL,
    user_type VARCHAR(20) NOT NULL DEFAULT 'PLATFORM',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_platform_user_type CHECK (user_type = 'PLATFORM'),
    CONSTRAINT ck_platform_user_global_role CHECK (global_role IN ('PLATFORM_OWNER', 'PLATFORM_SUPPORT')),
    CONSTRAINT uk_platform_user_user UNIQUE (user_id),
    CONSTRAINT fk_platform_user_user FOREIGN KEY (user_id, user_type) REFERENCES app_user (id, user_type)
);
