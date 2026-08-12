ALTER TABLE company_user
    ADD CONSTRAINT uk_company_user_user_company UNIQUE (user_id, company_id);

CREATE TABLE access_role (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_access_role_name_not_blank CHECK (LENGTH(BTRIM(name)) > 0),
    CONSTRAINT uk_access_role_id_company UNIQUE (id, company_id),
    CONSTRAINT fk_access_role_company FOREIGN KEY (company_id) REFERENCES company (id)
);

CREATE UNIQUE INDEX uk_access_role_company_name_normalized ON access_role (company_id, LOWER(name));
CREATE INDEX ix_access_role_company_id ON access_role (company_id);

CREATE TABLE permission (
    id UUID PRIMARY KEY,
    module VARCHAR(40) NOT NULL,
    action VARCHAR(40) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_permission_module_not_blank CHECK (LENGTH(BTRIM(module)) > 0),
    CONSTRAINT ck_permission_action_not_blank CHECK (LENGTH(BTRIM(action)) > 0),
    CONSTRAINT uk_permission_module_action UNIQUE (module, action)
);

CREATE TABLE role_permission (
    id UUID PRIMARY KEY,
    role_id UUID NOT NULL,
    permission_id UUID NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_role_permission UNIQUE (role_id, permission_id),
    CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES access_role (id),
    CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id) REFERENCES permission (id)
);

CREATE INDEX ix_role_permission_permission_id ON role_permission (permission_id);

CREATE TABLE user_role (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    company_id UUID NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_role UNIQUE (user_id, role_id),
    CONSTRAINT fk_user_role_company_user FOREIGN KEY (user_id, company_id)
        REFERENCES company_user (user_id, company_id),
    CONSTRAINT fk_user_role_role_company FOREIGN KEY (role_id, company_id)
        REFERENCES access_role (id, company_id)
);

CREATE INDEX ix_user_role_company_id ON user_role (company_id);
CREATE INDEX ix_user_role_role_id ON user_role (role_id);
