package com.renovar.canteiro.io.tenancy;

import com.renovar.canteiro.io.AbstractPostgresIntegrationTest;
import com.renovar.canteiro.io.identity.application.PasswordHasher;
import com.renovar.canteiro.io.identity.domain.CompanyUser;
import com.renovar.canteiro.io.identity.domain.CompanyUserRepository;
import com.renovar.canteiro.io.identity.domain.User;
import com.renovar.canteiro.io.identity.domain.UserRepository;
import com.renovar.canteiro.io.identity.domain.UserType;
import com.renovar.canteiro.io.platform.company.application.CompanyProfileApplicationService;
import com.renovar.canteiro.io.platform.company.application.UpdateCompanyCommand;
import com.renovar.canteiro.io.platform.company.domain.Company;
import com.renovar.canteiro.io.platform.company.domain.CompanyRepository;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import com.renovar.canteiro.io.tenancy.domain.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TenantCompanyIsolationIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String PASSWORD = "Canteiro#2026Seguro";

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private CompanyProfileApplicationService companyProfileApplicationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyUserRepository companyUserRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private TenantContextHolder tenantContextHolder;

    @AfterEach
    void clearTenantContext() {
        tenantContextHolder.clear();
    }

    @Test
    void readsOnlyTheCompanyFromTheCurrentTenantContext() {
        Company firstCompany = createCompany("First", "First trade name");
        Company secondCompany = createCompany("Second", "Second trade name");
        User firstCompanyUser = createCompanyUser(firstCompany, "first-company@example.com");
        tenantContextHolder.setCurrentTenant(new TenantContext(firstCompanyUser.getId(), firstCompany.getId()));

        Company companyReadByFirstTenant = companyProfileApplicationService.findCurrentCompany();

        assertEquals(firstCompany.getId(), companyReadByFirstTenant.getId());
        assertNotEquals(secondCompany.getId(), companyReadByFirstTenant.getId());
        assertNotEquals(secondCompany.getTradeName(), companyReadByFirstTenant.getTradeName());
    }

    @Test
    void mutatesOnlyTheCompanyFromTheCurrentTenantContext() {
        Company firstCompany = createCompany("First update", "First original trade name");
        Company secondCompany = createCompany("Second update", "Second original trade name");
        User firstCompanyUser = createCompanyUser(firstCompany, "first-update@example.com");
        tenantContextHolder.setCurrentTenant(new TenantContext(firstCompanyUser.getId(), firstCompany.getId()));

        Company updatedCompany = companyProfileApplicationService.updateCurrentCompany(new UpdateCompanyCommand(
                null,
                "First updated trade name",
                null,
                null,
                null,
                null,
                null
        ));
        Company persistedSecondCompany = companyRepository.findById(secondCompany.getId()).orElseThrow();

        assertEquals(firstCompany.getId(), updatedCompany.getId());
        assertEquals("First updated trade name", updatedCompany.getTradeName());
        assertEquals("Second original trade name", persistedSecondCompany.getTradeName());
    }

    private Company createCompany(String suffix, String tradeName) {
        return companyRepository.save(Company.create(
                suffix + " Company Ltda.",
                tradeName,
                Long.toUnsignedString(UUID.randomUUID().getLeastSignificantBits()),
                UUID.randomUUID() + "@example.com",
                null,
                null,
                null
        ));
    }

    private User createCompanyUser(Company company, String email) {
        User user = User.create(email, UserType.COMPANY);
        user.activate(passwordHasher.hash(PASSWORD), Instant.now());
        User persistedUser = userRepository.save(user);
        companyUserRepository.save(CompanyUser.create(persistedUser.getId(), company.getId()));
        return persistedUser;
    }
}
