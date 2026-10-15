ALTER TABLE measurement_item
    DROP CONSTRAINT IF EXISTS ck_measurement_item_unit_quantity;

ALTER TABLE measurement_item
    ADD CONSTRAINT ck_measurement_item_unit_quantity
        CHECK (
            (charge_type = 'UNIT'
                AND unit_quantity IS NOT NULL
                AND unit_quantity > 0
                AND area_square_meters IS NULL
                AND linear_meters IS NULL
                AND kilograms_per_square_meter IS NULL
                AND kilograms_per_linear_meter IS NULL
                AND total_weight_kg IS NULL)
            OR
            (charge_type <> 'UNIT'
                AND (unit_quantity IS NULL OR unit_quantity > 0))
        );
