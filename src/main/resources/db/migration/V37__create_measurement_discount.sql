ALTER TABLE measurement_version
    ADD COLUMN lock_version INTEGER NOT NULL DEFAULT 0;

CREATE TABLE measurement_discount (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    measurement_version_id UUID NOT NULL,
    discount_type VARCHAR(20) NOT NULL,
    discount_value NUMERIC(19, 4) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_measurement_discount_version_company FOREIGN KEY (measurement_version_id, company_id)
        REFERENCES measurement_version (id, company_id),
    CONSTRAINT uk_measurement_discount_version UNIQUE (measurement_version_id),
    CONSTRAINT ck_measurement_discount_type CHECK (discount_type IN ('FIXED', 'PERCENTAGE')),
    CONSTRAINT ck_measurement_discount_value_positive CHECK (discount_value > 0),
    CONSTRAINT ck_measurement_discount_percentage_range CHECK (
        discount_type <> 'PERCENTAGE' OR discount_value <= 100.0000
    )
);

CREATE INDEX ix_measurement_discount_company_version ON measurement_discount (company_id, measurement_version_id);
