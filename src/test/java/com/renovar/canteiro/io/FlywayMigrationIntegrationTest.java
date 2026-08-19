package com.renovar.canteiro.io;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FlywayMigrationIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private Flyway flyway;

    @Test
    void appliesAllMigrationsToThePostgreSqlContainer() {
        assertNotNull(flyway.info().current());
        assertEquals("42", flyway.info().current().getVersion().getVersion());
    }
}
