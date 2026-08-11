package com.renovar.canteiro.io;

import com.renovar.canteiro.io.platform.company.application.CompanyApplicationService;
import com.renovar.canteiro.io.platform.company.application.CreateCompanyCommand;
import com.renovar.canteiro.io.platform.company.application.UpdateCompanyCommand;
import com.renovar.canteiro.io.platform.company.domain.Company;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CompanyApplicationServiceIntegrationTest {

    @Autowired
    private CompanyApplicationService companyApplicationService;

    @Test
    void updatesAndDeactivatesTheSameCompanyRecord() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        int companiesBefore = companyApplicationService.findAll().size();

        Company created = companyApplicationService.create(new CreateCompanyCommand(
                "Company " + suffix,
                "Original trade name",
                "DOC-" + suffix,
                "company-" + suffix + "@example.test",
                "11999999999",
                "Original address",
                null
        ));

        Company updated = companyApplicationService.update(created.getId(), new UpdateCompanyCommand(
                null,
                "Updated trade name",
                null,
                null,
                null,
                null,
                null
        )).orElseThrow();

        Company persistedAfterUpdate = companyApplicationService.findById(created.getId()).orElseThrow();

        assertEquals(created.getId(), updated.getId());
        assertEquals(created.getId(), persistedAfterUpdate.getId());
        assertEquals("Updated trade name", persistedAfterUpdate.getTradeName());
        assertNotNull(persistedAfterUpdate.getCreatedAt());
        assertNotNull(persistedAfterUpdate.getUpdatedAt());
        assertEquals(companiesBefore + 1, companyApplicationService.findAll().size());

        companyApplicationService.deactivate(created.getId());

        Company persistedAfterDeactivation = companyApplicationService.findById(created.getId()).orElseThrow();
        assertEquals(created.getId(), persistedAfterDeactivation.getId());
        assertFalse(persistedAfterDeactivation.isActive());
        assertTrue(companyApplicationService.findById(created.getId()).isPresent());
    }
}
