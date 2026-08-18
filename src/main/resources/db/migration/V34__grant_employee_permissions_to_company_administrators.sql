INSERT INTO role_permission (id, role_id, permission_id, active)
SELECT md5(role.id::text || permission.id::text)::uuid,
       role.id,
       permission.id,
       TRUE
FROM access_role role
JOIN permission ON permission.module = 'EMPLOYEES'
WHERE role.name = 'Company Administrator'
ON CONFLICT (role_id, permission_id) DO NOTHING;
