package com.renovar.canteiro.io.payables;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("integration")
class PayableMigrationIntegrationTest {
    @Test void upgradesExistingTitlesWithoutRewritingAmountsStatusDatesOrOriginsAndPreservesContractCurrency(){
        try(var postgres=new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))){
            postgres.start();migrate(postgres,"86");
            var jdbc=new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()));
            UUID company=UUID.randomUUID(),customer=UUID.randomUUID(),work=UUID.randomUUID(),contract=UUID.randomUUID(),title=UUID.randomUUID();
            jdbc.update("insert into company(id,corporate_name,document,email) values (?,'Emitente legado','12345678901234','legado-payable@example.com')",company);
            jdbc.update("insert into final_customer(id,company_id,customer_type,name,document) values (?,?,'LEGAL','Cliente legado','12345678901234')",customer,company);
            jdbc.update("insert into obra(id,company_id,final_customer_id,name,status,execution_location_type) values (?,?,?,'Obra legada','ACTIVE','FINAL_CUSTOMER_LOCATION')",work,company,customer);
            jdbc.update("insert into contract(id,company_id,work_id,name,status,currency) values (?,?,?,'Contrato legado','ACTIVE','EUR')",contract,company,work);
            jdbc.update("insert into payable(id,company_id,financial_scope,work_id,contract_id,reference,due_on,original_amount,status) values(?,?,'WORK',?,?,'Obrigação legada','2026-10-01',123.45,'CANCELLED')",title,company,work,contract);
            var before=jdbc.queryForMap("select id,company_id,work_id,contract_id,tax_obligation_id,reference,description,due_on,original_amount,status,created_at,updated_at from payable where id=?",title);
            migrate(postgres,"87");
            assertEquals(before,jdbc.queryForMap("select id,company_id,work_id,contract_id,tax_obligation_id,reference,description,due_on,original_amount,status,created_at,updated_at from payable where id=?",title));
            assertEquals("EUR",jdbc.queryForObject("select currency from payable where id=?",String.class,title));
            assertEquals(title,jdbc.queryForObject("select financial_group_id from payable where id=?",UUID.class,title));
            assertEquals(0L,jdbc.queryForObject("select version from payable where id=?",Long.class,title));
            assertEquals(1,jdbc.queryForObject("select count(*) from payable_financial_group where id=? and operation_id is null",Integer.class,title));
            assertThrows(DataIntegrityViolationException.class,()->jdbc.update("delete from payable where id=?",title));
            assertThrows(DataIntegrityViolationException.class,()->jdbc.update("update payable set currency='BRL' where id=?",title));
        }
    }
    @Test void upgradesV91ThroughV93PreservingV92HistoryAndExistingRows(){
        try(var postgres=new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))){
            postgres.start();migrate(postgres,"91");
            var jdbc=new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()));
            UUID company=UUID.randomUUID(),other=UUID.randomUUID(),supplier=UUID.randomUUID(),title=UUID.randomUUID(),actor=UUID.randomUUID();
            jdbc.update("insert into company(id,corporate_name,document,email) values (?,'Emitente teste','TEST92','migration92@example.com')",company);
            jdbc.update("insert into company(id,corporate_name,document,email) values (?,'Outro teste','OTHER92','other92@example.com')",other);
            jdbc.update("insert into supplier(id,company_id,name,document) values (?,?,'Fornecedor preservado','DOC92')",supplier,company);
            jdbc.update("insert into app_user(id,email,user_type,status) values (?,'actor92@example.com','COMPANY','PENDING_ACTIVATION')",actor);
            jdbc.update("insert into company_user(id,user_id,company_id) values (?,?,?)",UUID.randomUUID(),actor,company);
            jdbc.update("insert into payable_financial_group(id,company_id) values (?,?)",title,company);
            jdbc.update("insert into payable(id,company_id,financial_group_id,supplier_id,financial_scope,reference,due_on,original_amount,status,currency) values (?,?,?,?,'COMPANY','Preservar','2026-12-01',123.45,'OPEN','BRL')",title,company,title,supplier);
            String before=jdbc.queryForObject("select to_jsonb(p)::text from payable p where id=?",String.class,title);
            var supplierBefore=jdbc.queryForMap("select id,company_id,name,document,active,version,created_at,updated_at from supplier where id=?",supplier);
            migrate(postgres,"92");
            assertEquals(before,jdbc.queryForObject("select to_jsonb(p)::text from payable p where id=?",String.class,title));
            assertEquals(supplierBefore,jdbc.queryForMap("select id,company_id,name,document,active,version,created_at,updated_at from supplier where id=?",supplier));
            assertEquals(0,jdbc.queryForObject("select count(*) from supplier where contact_name is not null or contact_phone is not null or contact_email is not null",Integer.class));
            String insert="insert into payable_payment_instruction(id,company_id,payable_id,version,type,ciphertext,created_by,created_at) values (?,?,?,1,'PIX','ciphertext-fixture',?,CURRENT_TIMESTAMP)";
            assertThrows(DataIntegrityViolationException.class,()->jdbc.update(insert,UUID.randomUUID(),other,title,actor));
            UUID id=UUID.randomUUID();jdbc.update(insert,id,company,title,actor);
            String instructionBefore = jdbc.queryForObject(
                    "select to_jsonb(p)::text from payable_payment_instruction p where id=?", String.class, id);
            assertEquals(-335592003, jdbc.queryForObject(
                    "select checksum from flyway_schema_history where version='92'", Integer.class));

            UUID foreignActor = UUID.randomUUID();
            jdbc.update("insert into app_user(id,email,user_type,status) values (?,'foreign93@example.com','COMPANY','PENDING_ACTIVATION')", foreignActor);
            jdbc.update("insert into company_user(id,user_id,company_id) values (?,?,?)", UUID.randomUUID(), foreignActor, other);
            migrate(postgres,"93");

            assertEquals(-335592003, jdbc.queryForObject(
                    "select checksum from flyway_schema_history where version='92'", Integer.class));
            assertEquals(instructionBefore, jdbc.queryForObject(
                    "select to_jsonb(p)::text from payable_payment_instruction p where id=?", String.class, id));
            String nextRevision = insert.replace("?,?,?,1", "?,?,?,2");
            assertThrows(DataIntegrityViolationException.class,
                    () -> jdbc.update(nextRevision, UUID.randomUUID(), company, title, foreignActor));
            String boleto = """
                    insert into payable_payment_instruction
                    (id,company_id,payable_id,version,type,ciphertext,created_by,created_at,pdf_storage_key,pdf_sha256,pdf_size)
                    values (?,?,?,2,'BOLETO','ciphertext-fixture',?,CURRENT_TIMESTAMP,'private/fixture',?,?)
                    """;
            String sha256 = "a".repeat(64);
            assertThrows(DataIntegrityViolationException.class,
                    () -> jdbc.update(boleto, UUID.randomUUID(), company, title, actor, null, 128L));
            assertThrows(DataIntegrityViolationException.class,
                    () -> jdbc.update(boleto, UUID.randomUUID(), company, title, actor, sha256, null));
            assertEquals(1, jdbc.update(boleto, UUID.randomUUID(), company, title, actor, sha256, 128L));
            assertThrows(DataIntegrityViolationException.class,()->jdbc.update(insert,UUID.randomUUID(),company,title,actor));
            assertThrows(DataIntegrityViolationException.class,()->jdbc.update("delete from payable_payment_instruction where id=?",id));
            assertThrows(DataIntegrityViolationException.class,()->jdbc.update("update payable_payment_instruction set ciphertext='changed' where id=?",id));
            assertEquals(before,jdbc.queryForObject("select to_jsonb(p)::text from payable p where id=?",String.class,title));
        }
    }

    @Test
    void upgradesAppliedV95WithoutRewritingHistoricalSettlements() {
        try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))) {
            postgres.start();
            migrate(postgres, "95");
            var jdbc = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
            UUID company = UUID.randomUUID(), actor = UUID.randomUUID(), title = UUID.randomUUID();
            UUID payment = UUID.randomUUID(), reversal = UUID.randomUUID();
            UUID paymentOperation = UUID.randomUUID(), reversalOperation = UUID.randomUUID(), nextOperation = UUID.randomUUID();
            jdbc.update("insert into company(id,corporate_name,document,email) values (?,'Teste','TEST95','migration95@example.com')", company);
            jdbc.update("insert into app_user(id,email,user_type,status) values (?,'actor95@example.com','COMPANY','PENDING_ACTIVATION')", actor);
            jdbc.update("insert into company_user(id,user_id,company_id) values (?,?,?)", UUID.randomUUID(), actor, company);
            jdbc.update("insert into payable_financial_group(id,company_id) values (?,?)", title, company);
            jdbc.update("""
                    insert into payable(id,company_id,financial_group_id,financial_scope,due_on,original_amount,status,currency)
                    values (?,?,?,'COMPANY','2026-11-12',100.00,'OPEN','BRL')
                    """, title, company, title);
            for (UUID operation : java.util.List.of(paymentOperation, reversalOperation, nextOperation)) {
                jdbc.update("insert into payable_settlement_operation(company_id,operation_id,payable_id,fingerprint) values (?,?,?,?)",
                        company, operation, title, "a".repeat(64));
            }
            String insert = """
                    insert into payable_settlement(id,company_id,payable_id,operation_id,reversal_of_id,amount,
                        effective_on,payment_method,recorded_by,recorded_at)
                    values (?,?,?,?,?,?,'2026-11-12','PIX',?,CURRENT_TIMESTAMP)
                    """;
            jdbc.update(insert, payment, company, title, paymentOperation, null, 100, actor);
            jdbc.update(insert, reversal, company, title, reversalOperation, payment, -100, actor);
            var history = jdbc.queryForList("select to_jsonb(s) - 'justification' as row from payable_settlement s order by id");

            migrate(postgres, "96");

            assertEquals(-1626293171, jdbc.queryForObject(
                    "select checksum from flyway_schema_history where version='95'", Integer.class));
            assertEquals(history, jdbc.queryForList("select to_jsonb(s) - 'justification' as row from payable_settlement s order by id"));
            assertEquals(true, jdbc.queryForObject("""
                    select condeferrable and condeferred from pg_constraint
                    where conname='payable_settlement_operation_change_request_id_company_id_fkey'
                    """, Boolean.class));
            assertThrows(DataIntegrityViolationException.class,
                    () -> jdbc.update(insert, UUID.randomUUID(), company, title, nextOperation, reversal, -100, actor));
        }
    }

    private void migrate(PostgreSQLContainer postgres,String target){Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).locations("classpath:db/migration").target(target).load().migrate();}
}
