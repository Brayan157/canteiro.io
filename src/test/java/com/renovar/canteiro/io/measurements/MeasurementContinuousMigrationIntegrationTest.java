package com.renovar.canteiro.io.measurements;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("integration")
class MeasurementContinuousMigrationIntegrationTest {
    @Test
    void upgradesActualV78CopiedLineagesWithoutTouchingOriginsOrIntentionalEmptyServices() {
        try(var postgres=new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))) {
            postgres.start();
            migrate(postgres,"77");
            JdbcTemplate jdbc=new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()));
            UUID company=UUID.randomUUID(),measurement=UUID.randomUUID();
            jdbc.update("insert into company(id,corporate_name,document,email) values (?,'Emitente de teste',?,?)",company,company.toString().substring(0,20),company+"@example.com");
            jdbc.update("insert into measurement(id,company_id,title) values (?,?,'Serviços Fábrica de Açúcar')",measurement,company);
            UUID previous=null;
            UUID[] sources=new UUID[5];
            for(int version=1;version<=6;version++) {
                UUID vid=UUID.randomUUID();
                String status=version<=3?"REJECTED":version<=5?"ACCEPTED":"DRAFT";
                jdbc.update("insert into measurement_version(id,company_id,measurement_id,version_number,status,previous_version_id) values (?,?,?,?,'DRAFT',?)",
                    vid,company,measurement,version,previous);
                if(version<=5)for(int item=0;item<5;item++) {
                    UUID id=UUID.randomUUID();String title=item<3?"Linha de Vinhaça":"Linha de cobre";
                    jdbc.update("""
                        insert into measurement_item(id,company_id,measurement_version_id,item_number,service_title,activity,
                            charge_type,linear_meters,unit_price,total_amount,calculation_formula,source_item_id,measured_on)
                        values (?,?,?,?,?,?,'LINEAR_METER',1,10,10,'1 x 10',?,current_date)
                        """,id,company,vid,item+1,title,"Atividade "+(item+1),sources[item]);
                    sources[item]=id;
                }
                if(version<=5)jdbc.update("update measurement_version set status=?,external_acceptance_on=current_date,external_acceptance_notes=? where id=?",
                    status,version<=3?"Correções históricas solicitadas":null,vid);
                if(version==4||version==5)for(int group=0;group<2;group++)
                    jdbc.update("insert into measurement_service(id,company_id,measurement_version_id,service_title,sort_order) values (?,?,?,?,?)",
                        UUID.randomUUID(),company,vid,group==0?"Linha de Vinhaça":"Linha de cobre",group==0?1:4);
                previous=vid;
            }
            migrate(postgres,"82");
            assertEquals(10,jdbc.queryForObject("select count(*) from measurement_continuous_service where measurement_id=?",Integer.class,measurement));
            List<String> beforeItems=snapshot(jdbc,"measurement_item");
            List<String> beforeBindings=snapshot(jdbc,"measurement_item_service");
            List<String> beforeRecognitions=snapshot(jdbc,"measurement_item_recognition");
            List<String> beforeGroups=snapshot(jdbc,"measurement_service");
            UUID intentional=UUID.randomUUID(),prepared=UUID.randomUUID(),ambiguous=UUID.randomUUID(),requested=UUID.randomUUID();
            // Even an empty user service bearing the same title and batch timestamp is preserved by its explicit audit origin.
            jdbc.update("""
                insert into measurement_continuous_service(id,company_id,measurement_id,service_title,sort_order,created_at,updated_at)
                select ?,company_id,measurement_id,service_title,20,created_at,created_at from measurement_continuous_service limit 1
                """,intentional);
            jdbc.update("""
                insert into audit_event(id,company_id,actor_type,module,action,entity_type,entity_id,occurred_at)
                values (?,?,'SYSTEM','MEASUREMENTS','CREATE','MeasurementContinuousService',?,current_timestamp)
                """,UUID.randomUUID(),company,intentional);
            jdbc.update("""
                insert into measurement_continuous_service(id,company_id,measurement_id,service_title,sort_order,destination_action,created_at,updated_at)
                select ?,company_id,measurement_id,'Preparado',21,'CREATE_NEW_SERVICE',created_at,created_at from measurement_continuous_service limit 1
                """,prepared);
            jdbc.update("insert into measurement_continuous_service(id,company_id,measurement_id,service_title,sort_order) values (?,?,?,'Legado sem prova',22)",
                ambiguous,company,measurement);
            jdbc.update("""
                insert into measurement_continuous_service(id,company_id,measurement_id,service_title,sort_order,created_at,updated_at)
                select ?,company_id,measurement_id,'Destino de solicitação',23,created_at,created_at from measurement_continuous_service limit 1
                """,requested);
            UUID requester=UUID.randomUUID();
            jdbc.update("insert into app_user(id,email,user_type,status) values (?,?,'COMPANY','PENDING_ACTIVATION')",requester,requester+"@example.com");
            jdbc.update("insert into company_user(id,user_id,company_id) values (?,?,?)",UUID.randomUUID(),requester,company);
            jdbc.update("""
                insert into change_request(id,company_id,requester_user_id,module,operation,entity_type,entity_version,status,proposed_data)
                values (?,?,?,'MEASUREMENTS','CREATE','MeasurementItem',0,'PENDING',
                    jsonb_build_object('items',jsonb_build_array(jsonb_build_object('continuousServiceId',?::text))))
                """,UUID.randomUUID(),company,requester,requested);
            List<String> beforeRequests=snapshot(jdbc,"change_request");
            migrate(postgres,"83");
            assertEquals(8,jdbc.queryForObject("select count(*) from measurement_continuous_service_retirement",Integer.class));
            assertEquals(14,jdbc.queryForObject("select count(*) from measurement_continuous_service",Integer.class));
            assertEquals(0,jdbc.queryForObject("select count(*) from measurement_continuous_service_retirement where continuous_service_id in (?,?,?,?)",
                Integer.class,intentional,prepared,ambiguous,requested));
            assertEquals(beforeItems,snapshot(jdbc,"measurement_item"));
            assertEquals(beforeBindings,snapshot(jdbc,"measurement_item_service"));
            assertEquals(beforeRecognitions,snapshot(jdbc,"measurement_item_recognition"));
            assertEquals(beforeGroups,snapshot(jdbc,"measurement_service"));
            assertEquals(beforeRequests,snapshot(jdbc,"change_request"));
            assertEquals(8,jdbc.queryForObject("select count(*) from audit_event where metadata->>'migration'='83' and actor_type='SYSTEM'",Integer.class));
            assertThrows(DataAccessException.class,()->jdbc.update("delete from measurement_continuous_service_retirement"));
            assertThrows(DataAccessException.class,()->jdbc.update("update measurement_continuous_service_retirement set retired_at=current_timestamp"));
            Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).target("83").load().validate();
        }
    }
    private void migrate(PostgreSQLContainer postgres,String target) {
        Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).target(target).load().migrate();
    }
    private List<String> snapshot(JdbcTemplate jdbc,String table) {
        return jdbc.queryForList("select row_to_json(t)::text from "+table+" t order by row_to_json(t)::text",String.class);
    }
}
