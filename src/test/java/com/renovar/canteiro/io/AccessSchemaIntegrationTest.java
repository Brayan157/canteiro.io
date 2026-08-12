package com.renovar.canteiro.io;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AccessSchemaIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void appliesAccessTablesAndCrossTenantAssignmentConstraints() {
        assertEquals(1, tableExists("access_role"));
        assertEquals(1, tableExists("permission"));
        assertEquals(1, tableExists("role_permission"));
        assertEquals(1, tableExists("user_role"));
        assertEquals(1, namedUniqueConstraintCount("permission", "uk_permission_module_action"));
        assertEquals(1, namedUniqueConstraintCount("user_role", "uk_user_role"));
        assertEquals(1, namedForeignKeyCount("access_role", "fk_access_role_company"));
        assertEquals(1, namedForeignKeyCount("role_permission", "fk_role_permission_role"));
        assertEquals(1, namedForeignKeyCount("role_permission", "fk_role_permission_permission"));
        assertEquals(1, namedForeignKeyCount("user_role", "fk_user_role_company_user"));
        assertEquals(1, namedForeignKeyCount("user_role", "fk_user_role_role_company"));
    }

    private Integer tableExists(String tableName) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM information_schema.tables
                        WHERE table_schema = 'public'
                          AND table_name = ?
                        """,
                Integer.class,
                tableName
        );
    }

    private Integer namedUniqueConstraintCount(String tableName, String constraintName) {
        return namedConstraintCount(tableName, constraintName, "UNIQUE");
    }

    private Integer namedForeignKeyCount(String tableName, String constraintName) {
        return namedConstraintCount(tableName, constraintName, "FOREIGN KEY");
    }

    private Integer namedConstraintCount(String tableName, String constraintName, String constraintType) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM information_schema.table_constraints
                        WHERE table_schema = 'public'
                          AND table_name = ?
                          AND constraint_name = ?
                          AND constraint_type = ?
                        """,
                Integer.class,
                tableName,
                constraintName,
                constraintType
        );
    }
}
