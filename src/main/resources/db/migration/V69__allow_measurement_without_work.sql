ALTER TABLE measurement
    ALTER COLUMN work_id DROP NOT NULL,
    ADD CONSTRAINT ck_measurement_contract_requires_work CHECK (
        contract_id IS NULL OR work_id IS NOT NULL
    );
