package com.renovar.canteiro.io;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompanySchemaIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void appliesCompanyConstraintsAndUtcTimestampColumns() {
        assertEquals("timestamp with time zone", columnDataType("created_at"));
        assertEquals("timestamp with time zone", columnDataType("updated_at"));
        assertEquals("NO", columnNullable("created_at"));
        assertEquals("NO", columnNullable("updated_at"));
        assertEquals(1, namedUniqueConstraintCount("uk_company_document"));
        assertEquals(1, namedUniqueConstraintCount("uk_company_email"));
    }

    private String columnDataType(String columnName) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT data_type
                        FROM information_schema.columns
                        WHERE table_schema = 'public'
                          AND table_name = 'company'
                          AND column_name = ?
                        """,
                String.class,
                columnName
        );
    }

    private String columnNullable(String columnName) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT is_nullable
                        FROM information_schema.columns
                        WHERE table_schema = 'public'
                          AND table_name = 'company'
                          AND column_name = ?
                        """,
                String.class,
                columnName
        );
    }

    private Integer namedUniqueConstraintCount(String constraintName) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM information_schema.table_constraints
                        WHERE table_schema = 'public'
                          AND table_name = 'company'
                          AND constraint_type = 'UNIQUE'
                          AND constraint_name = ?
                        """,
                Integer.class,
                constraintName
        );
    }
}
