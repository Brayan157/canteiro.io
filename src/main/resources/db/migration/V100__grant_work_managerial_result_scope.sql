ALTER TABLE work_access_grant DROP CONSTRAINT ck_work_grant_operation;
ALTER TABLE work_access_grant ADD CONSTRAINT ck_work_grant_operation CHECK (operation IN (
    'READ', 'CREATE', 'UPDATE', 'CLOSE', 'REOPEN', 'APPROVE', 'REJECT', 'REGISTER_OCCASIONAL', 'PLAN', 'READ_FINANCIAL'
));
