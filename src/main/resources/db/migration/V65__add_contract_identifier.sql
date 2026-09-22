alter table contract add column contract_identifier varchar(100);

comment on column contract.contract_identifier is 'Free-text business identifier supplied by the company; it is intentionally not unique.';
