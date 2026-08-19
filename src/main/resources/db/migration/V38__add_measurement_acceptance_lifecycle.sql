ALTER TABLE measurement_version
    ADD COLUMN external_acceptance_on DATE,
    ADD COLUMN external_acceptance_notes VARCHAR(1000);

ALTER TABLE measurement_version
    ADD CONSTRAINT ck_measurement_version_acceptance_data CHECK (
        status NOT IN ('ACCEPTED', 'REJECTED') OR external_acceptance_on IS NOT NULL
    );
