-- V75 materialized service groups for accepted versions that already existed at that time.
-- This migration also repairs accepted versions created by an older application instance after V75.
-- The unique key makes the backfill safe to run with rows already materialized.
INSERT INTO measurement_service (id, company_id, measurement_version_id, service_title, sort_order)
SELECT gen_random_uuid(), item.company_id, item.measurement_version_id, item.service_title,
       MIN(item.item_number)
FROM measurement_item item
JOIN measurement_version version ON version.id = item.measurement_version_id
    AND version.company_id = item.company_id
WHERE item.active = TRUE
  AND version.status = 'ACCEPTED'
  AND NOT EXISTS (
      SELECT 1
      FROM measurement_service service
      WHERE service.company_id = item.company_id
        AND service.measurement_version_id = item.measurement_version_id
        AND service.service_title = item.service_title
  )
GROUP BY item.company_id, item.measurement_version_id, item.service_title;
