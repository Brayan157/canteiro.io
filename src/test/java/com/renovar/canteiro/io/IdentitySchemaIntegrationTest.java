package com.renovar.canteiro.io;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IdentitySchemaIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void appliesIdentityTablesAndCompanyUserTenantConstraint() {
        assertEquals(1, tableExists("app_user"));
        assertEquals(1, tableExists("company_user"));
        assertEquals(1, tableExists("platform_user"));
        assertEquals(1, tableExists("account_activation_token"));
        assertEquals(1, tableExists("refresh_token"));
        assertEquals(1, tableExists("password_reset_token"));
        assertEquals(1, namedUniqueConstraintCount("company_user", "uk_company_user_user"));
        assertEquals(1, namedForeignKeyCount("company_user", "fk_company_user_company"));
        assertEquals(1, namedForeignKeyCount("company_user", "fk_company_user_user"));
        assertEquals(1, namedForeignKeyCount("platform_user", "fk_platform_user_user"));
        assertEquals(1, namedForeignKeyCount("refresh_token", "fk_refresh_token_user"));
        assertEquals(1, namedForeignKeyCount("password_reset_token", "fk_password_reset_token_user"));
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
