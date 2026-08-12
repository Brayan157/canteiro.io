CREATE TABLE subscription (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES company(id),
    status VARCHAR(30) NOT NULL,
    quoted_amount NUMERIC(19, 2) NOT NULL,
    pricing_source VARCHAR(30) NOT NULL,
    plan_bundle_id UUID REFERENCES plan_bundle(id),
    pricing_effective_date DATE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_subscription_status CHECK (status IN ('PENDING_ACTIVATION')),
    CONSTRAINT ck_subscription_quoted_amount_non_negative CHECK (quoted_amount >= 0),
    CONSTRAINT ck_subscription_pricing_source CHECK (
        pricing_source IN ('INDIVIDUAL_PLANS', 'PLAN_BUNDLE')
    ),
    CONSTRAINT ck_subscription_pricing_bundle CHECK (
        (pricing_source = 'PLAN_BUNDLE' AND plan_bundle_id IS NOT NULL)
        OR (pricing_source = 'INDIVIDUAL_PLANS' AND plan_bundle_id IS NULL)
    )
);

CREATE INDEX ix_subscription_company_id ON subscription (company_id);

CREATE TABLE subscription_item (
    id UUID PRIMARY KEY,
    subscription_id UUID NOT NULL REFERENCES subscription(id),
    plan_id UUID NOT NULL REFERENCES plan(id),
    plan_code VARCHAR(50) NOT NULL,
    plan_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_subscription_item_subscription_plan UNIQUE (subscription_id, plan_id),
    CONSTRAINT ck_subscription_item_plan_code_not_blank CHECK (LENGTH(BTRIM(plan_code)) > 0),
    CONSTRAINT ck_subscription_item_plan_name_not_blank CHECK (LENGTH(BTRIM(plan_name)) > 0)
);

CREATE INDEX ix_subscription_item_subscription_id ON subscription_item (subscription_id);

CREATE FUNCTION prevent_subscription_snapshot_mutation()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.company_id IS DISTINCT FROM OLD.company_id
        OR NEW.quoted_amount IS DISTINCT FROM OLD.quoted_amount
        OR NEW.pricing_source IS DISTINCT FROM OLD.pricing_source
        OR NEW.plan_bundle_id IS DISTINCT FROM OLD.plan_bundle_id
        OR NEW.pricing_effective_date IS DISTINCT FROM OLD.pricing_effective_date
        OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'Subscription price snapshot is immutable';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_subscription_snapshot_immutable
    BEFORE UPDATE ON subscription
    FOR EACH ROW
    EXECUTE FUNCTION prevent_subscription_snapshot_mutation();

CREATE FUNCTION prevent_subscription_item_snapshot_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Subscription item snapshot is immutable';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_subscription_item_snapshot_immutable
    BEFORE UPDATE OR DELETE ON subscription_item
    FOR EACH ROW
    EXECUTE FUNCTION prevent_subscription_item_snapshot_mutation();
