-- V78 is already applied. Preserve every identity and every financial/historical link.
-- This append-only ledger hides only proven backfill leftovers, never empty user services.
CREATE TABLE measurement_continuous_service_retirement (
    company_id UUID NOT NULL,
    measurement_id UUID NOT NULL,
    continuous_service_id UUID NOT NULL,
    source_migration VARCHAR(20) NOT NULL CHECK (source_migration = '78'),
    origin_created_at TIMESTAMPTZ NOT NULL,
    reason VARCHAR(100) NOT NULL CHECK (reason = 'V78_ORPHANED_COPY_GROUP'),
    retired_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (company_id, continuous_service_id),
    FOREIGN KEY (continuous_service_id, measurement_id, company_id)
        REFERENCES measurement_continuous_service(id, measurement_id, company_id)
);
CREATE TRIGGER trg_measurement_service_retirement_immutable
    BEFORE UPDATE OR DELETE ON measurement_continuous_service_retirement
    FOR EACH ROW EXECUTE FUNCTION prevent_measurement_origin_ledger_mutation();

-- CURRENT_TIMESTAMP is transaction-scoped: V78 services and immutable backfilled
-- recognitions have exactly the same creation instant. A retained service must
-- also contain an explicit copied-item lineage that predates that instant.
-- Application creation is separately audited; ambiguous legacy rows stay visible.
WITH backfill_batch AS (
    SELECT DISTINCT s.company_id, s.measurement_id, s.created_at
    FROM measurement_continuous_service s
    JOIN measurement_item_recognition r ON r.company_id = s.company_id
        AND r.measurement_id = s.measurement_id AND r.created_at = s.created_at
    WHERE EXISTS (
        SELECT 1 FROM measurement_item_service b
        JOIN measurement_item i ON i.id = b.measurement_item_id AND i.company_id = b.company_id
        JOIN measurement_item source ON source.id = i.source_item_id AND source.company_id = i.company_id
        JOIN measurement_version v ON v.id = i.measurement_version_id AND v.company_id = i.company_id
        JOIN measurement_version source_version ON source_version.id = source.measurement_version_id
            AND source_version.company_id = source.company_id AND source_version.measurement_id = v.measurement_id
        WHERE b.company_id = s.company_id AND b.measurement_id = s.measurement_id
            AND b.continuous_service_id = s.id AND i.created_at < s.created_at
            AND source.created_at < s.created_at AND v.created_at < s.created_at
    ) AND NOT EXISTS (
        SELECT 1 FROM audit_event a WHERE a.company_id = s.company_id AND a.entity_id = s.id
    )
)
INSERT INTO measurement_continuous_service_retirement
    (company_id, measurement_id, continuous_service_id, source_migration, origin_created_at, reason)
SELECT s.company_id, s.measurement_id, s.id, '78', s.created_at, 'V78_ORPHANED_COPY_GROUP'
FROM measurement_continuous_service s
JOIN backfill_batch batch ON batch.company_id = s.company_id
    AND batch.measurement_id = s.measurement_id AND batch.created_at = s.created_at
WHERE s.destination_action IS NULL AND s.contract_service_id IS NULL
    AND s.lock_version = 0 AND s.updated_at = s.created_at
    AND NOT EXISTS (SELECT 1 FROM measurement_item_service b
        WHERE b.company_id = s.company_id AND b.continuous_service_id = s.id)
    AND NOT EXISTS (SELECT 1 FROM measurement_item_recognition r
        WHERE r.company_id = s.company_id AND r.continuous_service_id = s.id)
    AND NOT EXISTS (SELECT 1 FROM measurement_service g
        WHERE g.company_id = s.company_id AND g.continuous_service_id = s.id)
    AND NOT EXISTS (SELECT 1 FROM audit_event a
        WHERE a.company_id = s.company_id AND a.entity_id = s.id)
    AND NOT EXISTS (SELECT 1 FROM change_request c WHERE c.company_id = s.company_id
        AND (c.entity_id = s.id OR jsonb_path_exists(c.proposed_data,
            '$.** ? (@ == $serviceId)', jsonb_build_object('serviceId', s.id::text))));

INSERT INTO audit_event (id, company_id, actor_type, module, action, entity_type, entity_id,
    after_data, metadata, occurred_at, actor_display_name, entity_display_name, event_summary, origin)
SELECT gen_random_uuid(), company_id, 'SYSTEM', 'MEASUREMENTS', 'CANCEL',
    'MeasurementContinuousService', continuous_service_id,
    jsonb_build_object('operationallyRetired', true),
    jsonb_build_object('origin', 'system', 'migration', '83', 'sourceMigration', '78',
        'measurementId', measurement_id, 'reason', reason),
    retired_at, 'Sistema', 'Serviço histórico sem vínculos',
    'Sistema retirou das consultas operacionais uma sobra de migração sem vínculos.', 'system'
FROM measurement_continuous_service_retirement;
