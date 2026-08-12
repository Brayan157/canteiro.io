WITH module_catalog(module_order, module) AS (
    VALUES
        (1, 'COMPANY'),
        (2, 'USERS'),
        (3, 'ROLES'),
        (4, 'CUSTOMERS'),
        (5, 'WORKS'),
        (6, 'CONTRACTS'),
        (7, 'SERVICES'),
        (8, 'DISCOUNTS'),
        (9, 'MEASUREMENTS'),
        (10, 'BILLING'),
        (11, 'INVOICES'),
        (12, 'RECEIVABLES'),
        (13, 'PAYABLES'),
        (14, 'COSTS_EXPENSES'),
        (15, 'REPORTING'),
        (16, 'AUDIT')
),
action_catalog(action_order, action, action_scope) AS (
    VALUES
        (1, 'READ', 'COMMON'),
        (2, 'EXPORT', 'COMMON'),
        (3, 'CREATE_DIRECT', 'EDITABLE'),
        (4, 'UPDATE_DIRECT', 'EDITABLE'),
        (5, 'REQUEST_CREATE', 'EDITABLE'),
        (6, 'REQUEST_UPDATE', 'EDITABLE'),
        (7, 'REQUEST_CANCEL', 'EDITABLE'),
        (8, 'APPROVE', 'APPROVAL'),
        (9, 'REJECT', 'APPROVAL'),
        (10, 'SEND_REPORT', 'REPORTING'),
        (11, 'MANAGE_USERS', 'USERS'),
        (12, 'MANAGE_ROLES', 'ROLES')
)
INSERT INTO permission (id, module, action, active)
SELECT (
    '00000000-0000-0000-0000-' || LPAD((module_order * 100 + action_order)::TEXT, 12, '0')
)::UUID,
       module,
       action,
       TRUE
FROM module_catalog
CROSS JOIN action_catalog
WHERE action_scope = 'COMMON'
   OR (action_scope = 'EDITABLE' AND module NOT IN ('REPORTING', 'AUDIT'))
   OR (
       action_scope = 'APPROVAL'
       AND module IN (
           'USERS',
           'CUSTOMERS',
           'WORKS',
           'CONTRACTS',
           'SERVICES',
           'DISCOUNTS',
           'MEASUREMENTS',
           'BILLING',
           'INVOICES',
           'RECEIVABLES',
           'PAYABLES',
           'COSTS_EXPENSES'
       )
   )
   OR (action_scope = 'REPORTING' AND module = 'REPORTING')
   OR (action_scope = 'USERS' AND module = 'USERS')
   OR (action_scope = 'ROLES' AND module = 'ROLES')
ON CONFLICT (module, action) DO NOTHING;
