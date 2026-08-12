CREATE TABLE plan_bundle (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_plan_bundle_code_format CHECK (code ~ '^[A-Z][A-Z0-9_]{0,49}$'),
    CONSTRAINT ck_plan_bundle_name_not_blank CHECK (LENGTH(BTRIM(name)) > 0),
    CONSTRAINT ck_plan_bundle_description_not_blank CHECK (
        description IS NULL OR LENGTH(BTRIM(description)) > 0
    ),
    CONSTRAINT uk_plan_bundle_code UNIQUE (code)
);

CREATE TABLE plan_bundle_item (
    id UUID PRIMARY KEY,
    plan_bundle_id UUID NOT NULL,
    plan_id UUID NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_plan_bundle_item UNIQUE (plan_bundle_id, plan_id),
    CONSTRAINT fk_plan_bundle_item_bundle FOREIGN KEY (plan_bundle_id) REFERENCES plan_bundle (id),
    CONSTRAINT fk_plan_bundle_item_plan FOREIGN KEY (plan_id) REFERENCES plan (id)
);

CREATE INDEX ix_plan_bundle_item_bundle_active ON plan_bundle_item (plan_bundle_id) WHERE active;

CREATE TABLE plan_bundle_price (
    id UUID PRIMARY KEY,
    plan_bundle_id UUID NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    valid_from DATE NOT NULL,
    valid_until DATE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_plan_bundle_price_amount_non_negative CHECK (amount >= 0),
    CONSTRAINT ck_plan_bundle_price_validity CHECK (valid_until IS NULL OR valid_until >= valid_from),
    CONSTRAINT fk_plan_bundle_price_bundle FOREIGN KEY (plan_bundle_id) REFERENCES plan_bundle (id)
);

CREATE INDEX ix_plan_bundle_price_bundle_valid_from ON plan_bundle_price (plan_bundle_id, valid_from);

ALTER TABLE plan_bundle_price
    ADD CONSTRAINT ex_plan_bundle_price_validity_no_overlap
    EXCLUDE USING gist (
        plan_bundle_id WITH =,
        daterange(valid_from, COALESCE(valid_until + 1, 'infinity'::date), '[)') WITH &&
    );
