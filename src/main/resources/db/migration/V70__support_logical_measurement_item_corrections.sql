ALTER TABLE measurement_item
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN source_item_id UUID;

ALTER TABLE measurement_item
    DROP CONSTRAINT uk_measurement_item_number;

ALTER TABLE measurement_item
    ADD CONSTRAINT fk_measurement_item_source_company
        FOREIGN KEY (source_item_id, company_id)
        REFERENCES measurement_item (id, company_id);

CREATE INDEX ix_measurement_item_company_version_active
    ON measurement_item (company_id, measurement_version_id, active);

CREATE UNIQUE INDEX ux_measurement_item_active_number
    ON measurement_item (measurement_version_id, item_number)
    WHERE active;
