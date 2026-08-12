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
        (14, 'COSTS_EXPENSES')
)
INSERT INTO permission (id, module, action, active)
SELECT (
    '00000000-0000-0000-0000-' || LPAD((module_order * 100 + 13)::TEXT, 12, '0')
)::UUID,
       module,
       'CANCEL_DIRECT',
       TRUE
FROM module_catalog
ON CONFLICT (module, action) DO NOTHING;
