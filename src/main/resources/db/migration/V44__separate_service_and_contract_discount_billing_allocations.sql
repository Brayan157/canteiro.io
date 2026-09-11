ALTER TABLE billing_allocation
    ADD COLUMN allocation_type VARCHAR(30);

UPDATE billing_allocation
SET allocation_type = 'SERVICE';

ALTER TABLE billing_allocation
    ALTER COLUMN allocation_type SET NOT NULL,
    ALTER COLUMN contract_service_id DROP NOT NULL,
    DROP CONSTRAINT ck_billing_allocation_amount_positive,
    ADD CONSTRAINT ck_billing_allocation_type CHECK (
        allocation_type IN ('SERVICE', 'CONTRACT_DISCOUNT')
    ),
    ADD CONSTRAINT ck_billing_allocation_type_amount_service CHECK (
        (allocation_type = 'SERVICE' AND contract_service_id IS NOT NULL AND allocated_amount > 0)
        OR (allocation_type = 'CONTRACT_DISCOUNT' AND contract_service_id IS NULL AND allocated_amount < 0)
    );
