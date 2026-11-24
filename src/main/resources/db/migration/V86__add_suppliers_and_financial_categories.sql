CREATE TABLE supplier (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES company(id),
    name VARCHAR(255) NOT NULL CHECK (length(trim(name)) > 0),
    document VARCHAR(50),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(id,company_id),
    CHECK(document IS NULL OR document ~ '^[A-Z0-9]{1,50}$')
);
CREATE INDEX ix_supplier_company_active_name ON supplier(company_id,active,lower(name),id);
CREATE INDEX ix_supplier_company_document ON supplier(company_id,document) WHERE document IS NOT NULL;

CREATE TABLE financial_category (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES company(id),
    code VARCHAR(50) NOT NULL CHECK(code ~ '^[A-Z0-9][A-Z0-9_-]{0,49}$'),
    name VARCHAR(255) NOT NULL CHECK(length(trim(name)) > 0),
    description VARCHAR(1000),
    category_type VARCHAR(30) NOT NULL CHECK(category_type IN ('WORK_COST','WORK_EXPENSE','COMPANY_EXPENSE','TAX')),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(id,company_id),
    UNIQUE(company_id,code)
);
CREATE INDEX ix_financial_category_company_active_name ON financial_category(company_id,active,lower(name),id);

CREATE FUNCTION protect_payable_catalog_history() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Payable catalog records cannot be physically deleted'; END IF;
    IF ROW(OLD.id,OLD.company_id,OLD.created_at) IS DISTINCT FROM ROW(NEW.id,NEW.company_id,NEW.created_at) THEN
        RAISE EXCEPTION 'Payable catalog identity is immutable';
    END IF;
    IF TG_TABLE_NAME='financial_category' THEN
        IF ROW(OLD.code,OLD.category_type) IS DISTINCT FROM ROW(NEW.code,NEW.category_type) THEN
            RAISE EXCEPTION 'Category code and classification are immutable';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_supplier_history BEFORE UPDATE OR DELETE ON supplier FOR EACH ROW EXECUTE FUNCTION protect_payable_catalog_history();
CREATE TRIGGER trg_financial_category_history BEFORE UPDATE OR DELETE ON financial_category FOR EACH ROW EXECUTE FUNCTION protect_payable_catalog_history();
