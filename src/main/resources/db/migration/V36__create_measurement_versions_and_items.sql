ALTER TABLE measurement
    ADD COLUMN status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    ADD CONSTRAINT ck_measurement_status CHECK (
        status IN ('DRAFT', 'GENERATED', 'SENT', 'PENDING_ACCEPTANCE', 'ACCEPTED', 'FINALIZED', 'REJECTED', 'CANCELLED')
    ),
    ADD CONSTRAINT uk_measurement_id_company UNIQUE (id, company_id);

CREATE TABLE measurement_version (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    measurement_id UUID NOT NULL,
    version_number INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_measurement_version_measurement_company FOREIGN KEY (measurement_id, company_id)
        REFERENCES measurement (id, company_id),
    CONSTRAINT uk_measurement_version_number UNIQUE (measurement_id, version_number),
    CONSTRAINT uk_measurement_version_id_company UNIQUE (id, company_id),
    CONSTRAINT ck_measurement_version_number_positive CHECK (version_number > 0),
    CONSTRAINT ck_measurement_version_status CHECK (
        status IN ('DRAFT', 'SENT', 'PENDING_ACCEPTANCE', 'ACCEPTED', 'REJECTED')
    )
);

CREATE TABLE measurement_item (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    measurement_version_id UUID NOT NULL,
    item_number INTEGER NOT NULL,
    activity VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    charge_type VARCHAR(40) NOT NULL,
    area_square_meters NUMERIC(18, 4),
    linear_meters NUMERIC(18, 4),
    kilograms_per_square_meter NUMERIC(18, 4),
    kilograms_per_linear_meter NUMERIC(18, 4),
    unit_price NUMERIC(19, 2),
    total_weight_kg NUMERIC(18, 4),
    total_amount NUMERIC(19, 2),
    calculation_formula VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_measurement_item_version_company FOREIGN KEY (measurement_version_id, company_id)
        REFERENCES measurement_version (id, company_id),
    CONSTRAINT uk_measurement_item_number UNIQUE (measurement_version_id, item_number),
    CONSTRAINT ck_measurement_item_number_positive CHECK (item_number > 0),
    CONSTRAINT ck_measurement_item_activity_not_blank CHECK (LENGTH(BTRIM(activity)) > 0),
    CONSTRAINT ck_measurement_item_description_not_blank CHECK (
        description IS NULL OR LENGTH(BTRIM(description)) > 0
    ),
    CONSTRAINT ck_measurement_item_charge_type CHECK (
        charge_type IN ('SQUARE_METER', 'LINEAR_METER', 'KILOGRAM_PER_SQUARE_METER', 'KILOGRAM_PER_LINEAR_METER')
    )
);

CREATE INDEX ix_measurement_version_company_measurement ON measurement_version (company_id, measurement_id);
CREATE INDEX ix_measurement_item_company_version ON measurement_item (company_id, measurement_version_id);
