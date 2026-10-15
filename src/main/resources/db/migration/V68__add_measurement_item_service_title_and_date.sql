ALTER TABLE measurement_item
    ADD COLUMN service_title VARCHAR(255),
    ADD COLUMN measured_on DATE;

UPDATE measurement_item
SET service_title = activity
WHERE service_title IS NULL;

UPDATE measurement_item item
SET measured_on = COALESCE(measurement.measured_on, item.created_at::date)
FROM measurement_version version
JOIN measurement ON measurement.id = version.measurement_id
    AND measurement.company_id = version.company_id
WHERE item.measurement_version_id = version.id
  AND item.company_id = version.company_id
  AND item.measured_on IS NULL;

ALTER TABLE measurement_item
    ALTER COLUMN service_title SET NOT NULL,
    ALTER COLUMN measured_on SET NOT NULL,
    ADD CONSTRAINT ck_measurement_item_service_title_not_blank CHECK (LENGTH(BTRIM(service_title)) > 0);
