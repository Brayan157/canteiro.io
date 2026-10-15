CREATE TABLE measurement_continuous_service (
    id UUID PRIMARY KEY, company_id UUID NOT NULL, measurement_id UUID NOT NULL,
    service_title VARCHAR(255) NOT NULL, sort_order INTEGER NOT NULL CHECK (sort_order > 0),
    destination_action VARCHAR(40), contract_service_id UUID, lock_version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (id, company_id), UNIQUE (id, measurement_id, company_id),
    FOREIGN KEY (measurement_id, company_id) REFERENCES measurement(id, company_id),
    FOREIGN KEY (contract_service_id, company_id) REFERENCES contract_service(id, company_id),
    CHECK (destination_action IS NULL OR destination_action IN ('CREATE_NEW_SERVICE','ADD_TO_EXISTING_SERVICE')),
    CHECK (destination_action IS DISTINCT FROM 'ADD_TO_EXISTING_SERVICE' OR contract_service_id IS NOT NULL)
);
ALTER TABLE measurement_version ADD CONSTRAINT uk_measurement_version_measurement_company UNIQUE(id,measurement_id,company_id);
ALTER TABLE measurement_item ADD CONSTRAINT uk_measurement_item_version_company UNIQUE(id,measurement_version_id,company_id);
CREATE TABLE measurement_item_service (
    measurement_item_id UUID NOT NULL, company_id UUID NOT NULL, measurement_id UUID NOT NULL,
    measurement_version_id UUID NOT NULL, continuous_service_id UUID NOT NULL,
    PRIMARY KEY (measurement_item_id,company_id),
    FOREIGN KEY (measurement_item_id,measurement_version_id,company_id) REFERENCES measurement_item(id,measurement_version_id,company_id),
    FOREIGN KEY (measurement_version_id,measurement_id,company_id) REFERENCES measurement_version(id,measurement_id,company_id),
    FOREIGN KEY (continuous_service_id,measurement_id,company_id) REFERENCES measurement_continuous_service(id,measurement_id,company_id)
);
CREATE INDEX ix_measurement_item_service_continuity ON measurement_item_service(company_id,measurement_id,continuous_service_id);
-- Legacy groups receive distinct identities per version. Repeated titles never infer financial continuity.
CREATE TEMP TABLE legacy_continuous_group ON COMMIT DROP AS
SELECT gen_random_uuid() id,i.company_id,v.measurement_id,i.measurement_version_id,i.service_title,MIN(i.item_number) sort_order
FROM measurement_item i JOIN measurement_version v ON v.id=i.measurement_version_id AND v.company_id=i.company_id
GROUP BY i.company_id,v.measurement_id,i.measurement_version_id,i.service_title;
INSERT INTO measurement_continuous_service(id,company_id,measurement_id,service_title,sort_order)
SELECT id,company_id,measurement_id,service_title,sort_order FROM legacy_continuous_group;
INSERT INTO measurement_item_service
SELECT i.id,i.company_id,g.measurement_id,i.measurement_version_id,g.id FROM measurement_item i
JOIN legacy_continuous_group g ON g.company_id=i.company_id AND g.measurement_version_id=i.measurement_version_id AND g.service_title=i.service_title;
-- Copies inherit only an explicit source-item chain, not a title match across versions.
WITH RECURSIVE lineage AS (
 SELECT i.id,i.company_id,i.id root FROM measurement_item i WHERE i.source_item_id IS NULL
 UNION ALL SELECT i.id,i.company_id,l.root FROM measurement_item i JOIN lineage l ON i.source_item_id=l.id AND i.company_id=l.company_id
)
UPDATE measurement_item_service target SET continuous_service_id=source.continuous_service_id
FROM lineage l JOIN measurement_item_service source ON source.measurement_item_id=l.root AND source.company_id=l.company_id
WHERE target.measurement_item_id=l.id AND target.company_id=l.company_id;
ALTER TABLE measurement_service ADD COLUMN continuous_service_id UUID;
ALTER TABLE measurement_service ADD CONSTRAINT fk_measurement_service_continuity_company
 FOREIGN KEY(continuous_service_id,company_id) REFERENCES measurement_continuous_service(id,company_id);
UPDATE measurement_service g SET continuous_service_id=l.continuous_service_id
FROM measurement_item i JOIN measurement_item_service l ON l.measurement_item_id=i.id AND l.company_id=i.company_id
WHERE i.company_id=g.company_id AND i.measurement_version_id=g.measurement_version_id AND i.service_title=g.service_title;
ALTER TABLE measurement_service DROP CONSTRAINT uk_measurement_service_version_title;
CREATE UNIQUE INDEX uk_measurement_service_version_continuity ON measurement_service(company_id,measurement_version_id,continuous_service_id);
-- Only pre-existing explicit composition mappings may initialize a destination.
UPDATE measurement_continuous_service s SET destination_action='ADD_TO_EXISTING_SERVICE',contract_service_id=c.contract_service_id
FROM measurement_service g JOIN measurement_contract_composition c ON c.measurement_service_id=g.id AND c.company_id=g.company_id
WHERE s.id=g.continuous_service_id AND s.company_id=g.company_id;
CREATE TABLE measurement_item_recognition (
 id UUID PRIMARY KEY, company_id UUID NOT NULL, measurement_id UUID NOT NULL, measurement_version_id UUID NOT NULL,
 measurement_item_id UUID NOT NULL, root_item_id UUID NOT NULL, continuous_service_id UUID NOT NULL,
 gross_amount NUMERIC(19,2) NOT NULL CHECK(gross_amount>=0),net_amount NUMERIC(19,2) NOT NULL CHECK(net_amount>=0),
 total_weight_kg NUMERIC(18,4),active BOOLEAN NOT NULL,version_number INTEGER NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(company_id,measurement_item_id),UNIQUE(company_id,measurement_version_id,root_item_id),
 FOREIGN KEY(measurement_item_id,measurement_version_id,company_id) REFERENCES measurement_item(id,measurement_version_id,company_id),
 FOREIGN KEY(measurement_version_id,measurement_id,company_id) REFERENCES measurement_version(id,measurement_id,company_id),
 FOREIGN KEY(root_item_id,company_id) REFERENCES measurement_item(id,company_id),
 FOREIGN KEY(continuous_service_id,measurement_id,company_id) REFERENCES measurement_continuous_service(id,measurement_id,company_id)
);
CREATE INDEX ix_measurement_recognition_effective ON measurement_item_recognition(company_id,measurement_id,root_item_id,version_number DESC);
WITH RECURSIVE lineage AS (
 SELECT i.id,i.company_id,i.id root FROM measurement_item i WHERE i.source_item_id IS NULL
 UNION ALL SELECT i.id,i.company_id,l.root FROM measurement_item i JOIN lineage l ON i.source_item_id=l.id AND i.company_id=l.company_id
), amounts AS (
 SELECT i.*,v.measurement_id,v.version_number,l.root,b.continuous_service_id,
 SUM(CASE WHEN i.active THEN i.total_amount ELSE 0 END) OVER(PARTITION BY i.company_id,i.measurement_version_id) gross,
 ROW_NUMBER() OVER(PARTITION BY i.company_id,i.measurement_version_id ORDER BY i.active DESC,i.item_number,i.id) rn,
 COUNT(*) FILTER(WHERE i.active) OVER(PARTITION BY i.company_id,i.measurement_version_id) active_count,
 d.discount_type,d.discount_value
 FROM measurement_item i JOIN measurement_version v ON v.id=i.measurement_version_id AND v.company_id=i.company_id AND v.status='ACCEPTED'
 JOIN lineage l ON l.id=i.id AND l.company_id=i.company_id
 JOIN measurement_item_service b ON b.measurement_item_id=i.id AND b.company_id=i.company_id
 LEFT JOIN measurement_discount d ON d.measurement_version_id=i.measurement_version_id AND d.company_id=i.company_id
), shares AS (
 SELECT a.*,CASE WHEN discount_type='FIXED' THEN discount_value WHEN discount_type='PERCENTAGE' THEN ROUND(gross*discount_value/100,2) ELSE 0 END discount
 FROM amounts a
), rounded AS (
 SELECT s.*,CASE WHEN active AND gross>0 THEN ROUND(total_amount*discount/gross,2) ELSE 0 END share FROM shares s
)
INSERT INTO measurement_item_recognition(id,company_id,measurement_id,measurement_version_id,measurement_item_id,root_item_id,continuous_service_id,gross_amount,net_amount,total_weight_kg,active,version_number)
SELECT gen_random_uuid(),company_id,measurement_id,measurement_version_id,id,root,continuous_service_id,
 CASE WHEN active THEN total_amount ELSE 0 END,
 CASE WHEN NOT active THEN 0 WHEN rn=active_count THEN total_amount-discount+SUM(CASE WHEN rn<active_count THEN share ELSE 0 END) OVER(PARTITION BY company_id,measurement_version_id) ELSE total_amount-share END,
 CASE WHEN active THEN total_weight_kg ELSE NULL END,active,version_number FROM rounded;
CREATE TABLE measurement_composition_adjustment (
 id UUID PRIMARY KEY,company_id UUID NOT NULL,composition_id UUID NOT NULL,corrective_version_id UUID NOT NULL,
 net_amount NUMERIC(19,2) NOT NULL CHECK(net_amount<=0),reason VARCHAR(2000) NOT NULL,actor_user_id UUID,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(company_id,composition_id),
 FOREIGN KEY(composition_id,company_id) REFERENCES measurement_contract_composition(id,company_id),
 FOREIGN KEY(corrective_version_id,company_id) REFERENCES measurement_version(id,company_id)
);
ALTER TABLE change_request ADD CONSTRAINT uk_change_request_id_company UNIQUE(id,company_id);
CREATE TABLE measurement_composition_request (
 company_id UUID NOT NULL,measurement_service_id UUID NOT NULL,change_request_id UUID NOT NULL,
 PRIMARY KEY(company_id,measurement_service_id),
 FOREIGN KEY(measurement_service_id,company_id) REFERENCES measurement_service(id,company_id),
 FOREIGN KEY(change_request_id,company_id) REFERENCES change_request(id,company_id)
);
CREATE OR REPLACE FUNCTION prevent_measurement_origin_ledger_mutation() RETURNS TRIGGER AS $$
BEGIN RAISE EXCEPTION 'Measurement origin ledger is append-only'; END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_measurement_recognition_immutable BEFORE UPDATE OR DELETE ON measurement_item_recognition
 FOR EACH ROW EXECUTE FUNCTION prevent_measurement_origin_ledger_mutation();
CREATE TRIGGER trg_measurement_composition_adjustment_immutable BEFORE UPDATE OR DELETE ON measurement_composition_adjustment
 FOR EACH ROW EXECUTE FUNCTION prevent_measurement_origin_ledger_mutation();

