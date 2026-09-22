ALTER TABLE employee_payment_account
    ADD COLUMN account_check_digit_ciphertext TEXT;

INSERT INTO permission (id, module, action, active)
VALUES ('00000000-0000-0000-0000-000000001714', 'EMPLOYEES', 'VIEW_PAYMENT_DETAILS', TRUE)
ON CONFLICT (module, action) DO NOTHING;

INSERT INTO role_permission (id, role_id, permission_id, active)
SELECT md5(role.id::text || permission.id::text)::uuid,
       role.id,
       permission.id,
       TRUE
FROM access_role role
JOIN permission ON permission.module = 'EMPLOYEES' AND permission.action = 'VIEW_PAYMENT_DETAILS'
WHERE role.name = 'Company Administrator'
ON CONFLICT (role_id, permission_id) DO NOTHING;
