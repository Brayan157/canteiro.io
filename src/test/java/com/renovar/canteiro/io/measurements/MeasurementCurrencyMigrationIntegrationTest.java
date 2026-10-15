package com.renovar.canteiro.io.measurements;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("integration")
class MeasurementCurrencyMigrationIntegrationTest {
    @Test void labelsLegacyRootsAsBrlWithoutChangingHistoricalItemsAndRejectsCurrencyMutationOrMismatch() {
        try(var postgres=new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))) {
            postgres.start();migrate(postgres,"83");
            var jdbc=new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()));
            UUID company=UUID.randomUUID(),customer=UUID.randomUUID(),work=UUID.randomUUID(),contract=UUID.randomUUID(),mid=UUID.randomUUID(),vid=UUID.randomUUID(),iid=UUID.randomUUID();
            jdbc.update("insert into company(id,corporate_name,document,email) values (?,'Emitente legado','12345678901234','legado@example.com')",company);
            jdbc.update("insert into final_customer(id,company_id,customer_type,name,document) values (?,?,'LEGAL','Cliente legado','12345678901234')",customer,company);
            jdbc.update("insert into obra(id,company_id,final_customer_id,name,status,execution_location_type) values (?,?,?,'Obra legada','ACTIVE','FINAL_CUSTOMER_LOCATION')",work,company,customer);
            jdbc.update("insert into contract(id,company_id,work_id,name,status) values (?,?,?,'Contrato legado','ACTIVE')",contract,company,work);
            jdbc.update("insert into measurement(id,company_id,work_id,contract_id,title) values (?,?,?,?,'Medição legada')",mid,company,work,contract);
            jdbc.update("insert into measurement_version(id,company_id,measurement_id,version_number,status) values (?,?,?,1,'DRAFT')",vid,company,mid);
            jdbc.update("""
                insert into measurement_item(id,company_id,measurement_version_id,item_number,service_title,activity,charge_type,
                    linear_meters,unit_price,total_amount,calculation_formula,measured_on) values (?,?,?,1,'Serviço legado','Atividade legada','LINEAR_METER',3,25,75,'3 x 25',current_date)
                """,iid,company,vid);
            jdbc.update("update measurement_version set status='ACCEPTED',external_acceptance_on=current_date where id=?",vid);
            var before=jdbc.queryForMap("select id,unit_price,total_amount,linear_meters,active,created_at,updated_at from measurement_item where id=?",iid);
            migrate(postgres,"84");
            assertEquals("BRL",jdbc.queryForObject("select currency from contract where id=?",String.class,contract));
            assertEquals("BRL",jdbc.queryForObject("select currency from measurement where id=?",String.class,mid));
            assertEquals(before,jdbc.queryForMap("select id,unit_price,total_amount,linear_meters,active,created_at,updated_at from measurement_item where id=?",iid));
            assertEquals(new BigDecimal("75.00"),jdbc.queryForObject("select total_amount from measurement_item where id=?",BigDecimal.class,iid));
            assertThrows(DataIntegrityViolationException.class,()->jdbc.update("update measurement set currency='USD' where id=?",mid));
            assertThrows(DataIntegrityViolationException.class,()->jdbc.update("insert into measurement(id,company_id,work_id,contract_id,title,currency) values (?,?,?,?,'Incompatível','EUR')",UUID.randomUUID(),company,work,contract));
            assertThrows(DataIntegrityViolationException.class,()->jdbc.update("insert into contract(id,company_id,work_id,name,status,currency) values (?,?,?,'Inválido','ACTIVE','GBP')",UUID.randomUUID(),company,work));
        }
    }

    private void migrate(PostgreSQLContainer postgres,String target) {
        Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword())
                .locations("classpath:db/migration").target(target).load().migrate();
    }
}
