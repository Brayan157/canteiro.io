ALTER TABLE contract
    ADD COLUMN pricing_mode VARCHAR(30) NOT NULL DEFAULT 'FIXED',
    ADD CONSTRAINT ck_contract_pricing_mode CHECK (pricing_mode IN ('FIXED', 'MEASUREMENT_BASED'));

ALTER TABLE contract_service
    ADD COLUMN measurement_composition_amount NUMERIC(19, 2) NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_contract_service_measurement_composition_amount_non_negative
        CHECK (measurement_composition_amount >= 0);

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE measurement_service (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    measurement_version_id UUID NOT NULL,
    service_title VARCHAR(255) NOT NULL,
    sort_order INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_measurement_service_version_title UNIQUE (company_id, measurement_version_id, service_title),
    CONSTRAINT uk_measurement_service_id_company UNIQUE (id, company_id),
    CONSTRAINT fk_measurement_service_version_company FOREIGN KEY (measurement_version_id, company_id)
        REFERENCES measurement_version (id, company_id),
    CONSTRAINT ck_measurement_service_sort_order_positive CHECK (sort_order > 0)
);

CREATE INDEX ix_measurement_service_version_order
    ON measurement_service (company_id, measurement_version_id, sort_order);

-- Existing accepted versions were frozen with title-only groups. Materialize one stable group for each title.
INSERT INTO measurement_service (id, company_id, measurement_version_id, service_title, sort_order)
SELECT gen_random_uuid(), item.company_id, item.measurement_version_id, item.service_title,
       MIN(item.item_number)
FROM measurement_item item
JOIN measurement_version version ON version.id = item.measurement_version_id
    AND version.company_id = item.company_id
WHERE item.active = TRUE AND version.status = 'ACCEPTED'
GROUP BY item.company_id, item.measurement_version_id, item.service_title;

CREATE TABLE measurement_contract_composition (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    measurement_id UUID NOT NULL,
    measurement_version_id UUID NOT NULL,
    measurement_service_id UUID NOT NULL,
    contract_id UUID NOT NULL,
    contract_service_id UUID NOT NULL,
    origin_type VARCHAR(40) NOT NULL,
    gross_amount NUMERIC(19, 2) NOT NULL,
    net_amount NUMERIC(19, 2) NOT NULL,
    total_weight_kg NUMERIC(18, 4),
    item_count INTEGER NOT NULL,
    justification VARCHAR(2000),
    created_by_user_id UUID,
    reversed_at TIMESTAMP WITH TIME ZONE,
    reversal_reason VARCHAR(2000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_measurement_contract_composition_group UNIQUE (company_id, measurement_service_id),
    CONSTRAINT uk_measurement_contract_composition_id_company UNIQUE (id, company_id),
    CONSTRAINT ck_measurement_contract_composition_origin CHECK (origin_type IN ('MEASUREMENT_PRICING', 'CONTRACT_ADDITION')),
    CONSTRAINT ck_measurement_contract_composition_amounts_non_negative CHECK (gross_amount >= 0 AND net_amount >= 0),
    CONSTRAINT ck_measurement_contract_composition_item_count_positive CHECK (item_count > 0),
    CONSTRAINT fk_measurement_contract_composition_measurement_company FOREIGN KEY (measurement_id, company_id)
        REFERENCES measurement (id, company_id),
    CONSTRAINT fk_measurement_contract_composition_version_company FOREIGN KEY (measurement_version_id, company_id)
        REFERENCES measurement_version (id, company_id),
    CONSTRAINT fk_measurement_contract_composition_service_group_company FOREIGN KEY (measurement_service_id, company_id)
        REFERENCES measurement_service (id, company_id),
    CONSTRAINT fk_measurement_contract_composition_contract_company FOREIGN KEY (contract_id, company_id)
        REFERENCES contract (id, company_id),
    CONSTRAINT fk_measurement_contract_composition_contract_service_company FOREIGN KEY (contract_service_id, company_id)
        REFERENCES contract_service (id, company_id)
);

CREATE TABLE measurement_contract_composition_item (
    composition_id UUID NOT NULL,
    company_id UUID NOT NULL,
    measurement_item_id UUID NOT NULL,
    PRIMARY KEY (composition_id, measurement_item_id),
    CONSTRAINT fk_measurement_contract_composition_item_composition_company FOREIGN KEY (composition_id, company_id)
        REFERENCES measurement_contract_composition (id, company_id),
    CONSTRAINT fk_measurement_contract_composition_item_item_company FOREIGN KEY (measurement_item_id, company_id)
        REFERENCES measurement_item (id, company_id)
);

CREATE INDEX ix_measurement_contract_composition_contract
    ON measurement_contract_composition (company_id, contract_id, contract_service_id);
