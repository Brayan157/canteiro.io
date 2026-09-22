ALTER TABLE company_user
    ADD COLUMN company_owner BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE access_role
    ADD COLUMN system_protected BOOLEAN NOT NULL DEFAULT FALSE;

-- Onboarding is the authoritative historical record for the initial owner.
UPDATE company_user company_user_link
SET company_owner = TRUE
FROM audit_event onboarding_event
WHERE onboarding_event.company_id = company_user_link.company_id
  AND onboarding_event.actor_user_id = company_user_link.user_id
  AND onboarding_event.module = 'COMPANY'
  AND onboarding_event.action = 'CREATE'
  AND onboarding_event.entity_type = 'Company'
  AND onboarding_event.entity_id = company_user_link.company_id
  AND onboarding_event.after_data ? 'initialOwnerRoleId';

-- The role referenced by that immutable onboarding event is the protected owner role.
UPDATE access_role role
SET system_protected = TRUE
FROM audit_event onboarding_event
WHERE onboarding_event.company_id = role.company_id
  AND onboarding_event.module = 'COMPANY'
  AND onboarding_event.action = 'CREATE'
  AND onboarding_event.entity_type = 'Company'
  AND onboarding_event.after_data ->> 'initialOwnerRoleId' = role.id::text;

CREATE UNIQUE INDEX uk_company_user_company_owner
    ON company_user (company_id)
    WHERE company_owner;

CREATE UNIQUE INDEX uk_access_role_company_system_protected
    ON access_role (company_id)
    WHERE system_protected;
