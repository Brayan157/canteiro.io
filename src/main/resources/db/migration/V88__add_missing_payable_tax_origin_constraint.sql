-- Compatibility migration for databases that applied an earlier V87 variant.
-- Fresh databases already receive this constraint from V87 and therefore skip this block.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'payable'::regclass
          AND conname = 'ck_payable_tax_origin_links'
    ) THEN
        ALTER TABLE payable
            ADD CONSTRAINT ck_payable_tax_origin_links CHECK (
                tax_obligation_id IS NULL
                OR (financial_scope = 'WORK' AND work_id IS NOT NULL AND contract_id IS NOT NULL)
            );
    END IF;
END $$;
