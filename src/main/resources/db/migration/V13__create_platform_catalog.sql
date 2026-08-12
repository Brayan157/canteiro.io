CREATE TABLE plan (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_plan_code_format CHECK (code ~ '^[A-Z][A-Z0-9_]{0,49}$'),
    CONSTRAINT ck_plan_name_not_blank CHECK (LENGTH(BTRIM(name)) > 0),
    CONSTRAINT ck_plan_description_not_blank CHECK (description IS NULL OR LENGTH(BTRIM(description)) > 0),
    CONSTRAINT uk_plan_code UNIQUE (code)
);

CREATE TABLE plan_feature (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    feature_type VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_plan_feature_code_format CHECK (code ~ '^[A-Z][A-Z0-9_]{0,49}$'),
    CONSTRAINT ck_plan_feature_type CHECK (feature_type IN ('MODULE', 'FEATURE')),
    CONSTRAINT ck_plan_feature_name_not_blank CHECK (LENGTH(BTRIM(name)) > 0),
    CONSTRAINT ck_plan_feature_description_not_blank CHECK (
        description IS NULL OR LENGTH(BTRIM(description)) > 0
    ),
    CONSTRAINT uk_plan_feature_code UNIQUE (code)
);

CREATE TABLE plan_feature_assignment (
    id UUID PRIMARY KEY,
    plan_id UUID NOT NULL,
    plan_feature_id UUID NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_plan_feature_assignment UNIQUE (plan_id, plan_feature_id),
    CONSTRAINT fk_plan_feature_assignment_plan FOREIGN KEY (plan_id) REFERENCES plan (id),
    CONSTRAINT fk_plan_feature_assignment_feature FOREIGN KEY (plan_feature_id) REFERENCES plan_feature (id)
);

CREATE INDEX ix_plan_feature_assignment_plan_active ON plan_feature_assignment (plan_id) WHERE active;

CREATE TABLE plan_price (
    id UUID PRIMARY KEY,
    plan_id UUID NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    valid_from DATE NOT NULL,
    valid_until DATE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_plan_price_amount_non_negative CHECK (amount >= 0),
    CONSTRAINT ck_plan_price_validity CHECK (valid_until IS NULL OR valid_until >= valid_from),
    CONSTRAINT fk_plan_price_plan FOREIGN KEY (plan_id) REFERENCES plan (id)
);

CREATE INDEX ix_plan_price_plan_valid_from ON plan_price (plan_id, valid_from);

CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE plan_price
    ADD CONSTRAINT ex_plan_price_validity_no_overlap
    EXCLUDE USING gist (
        plan_id WITH =,
        daterange(valid_from, COALESCE(valid_until + 1, 'infinity'::date), '[)') WITH &&
    );
