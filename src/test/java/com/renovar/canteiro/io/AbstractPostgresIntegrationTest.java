package com.renovar.canteiro.io;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@Tag("integration")
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
abstract class AbstractPostgresIntegrationTest {
}
