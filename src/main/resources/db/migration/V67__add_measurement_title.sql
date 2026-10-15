ALTER TABLE measurement
    ADD COLUMN title VARCHAR(200);

UPDATE measurement
SET title = LEFT(COALESCE(NULLIF(BTRIM(reference), ''), NULLIF(BTRIM(description), ''), 'Medição sem título'), 200)
WHERE title IS NULL;

ALTER TABLE measurement
    ALTER COLUMN title SET NOT NULL,
    ADD CONSTRAINT ck_measurement_title_not_blank CHECK (LENGTH(BTRIM(title)) > 0);
