ALTER TABLE obra
    ADD COLUMN execution_location_type VARCHAR(30),
    ADD COLUMN execution_address VARCHAR(500);

UPDATE obra
SET execution_location_type = 'FINAL_CUSTOMER_LOCATION'
WHERE execution_location_type IS NULL;

ALTER TABLE obra
    ALTER COLUMN execution_location_type SET NOT NULL;

ALTER TABLE obra
    ADD CONSTRAINT ck_obra_execution_location_type CHECK (
        execution_location_type IN ('FINAL_CUSTOMER_LOCATION', 'PROVIDER_UNIT', 'OTHER_ADDRESS')
    ),
    ADD CONSTRAINT ck_obra_execution_address CHECK (
        execution_location_type IS NULL
        OR execution_location_type <> 'OTHER_ADDRESS'
        OR execution_address IS NOT NULL AND LENGTH(BTRIM(execution_address)) > 0
    );
