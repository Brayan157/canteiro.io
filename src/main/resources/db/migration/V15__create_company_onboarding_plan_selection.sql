CREATE TABLE company_onboarding_plan_selection (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES company(id),
    plan_id UUID NOT NULL REFERENCES plan(id),
    selected_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_company_onboarding_plan_selection UNIQUE (company_id, plan_id)
);

CREATE INDEX idx_company_onboarding_plan_selection_company_id
    ON company_onboarding_plan_selection (company_id);
