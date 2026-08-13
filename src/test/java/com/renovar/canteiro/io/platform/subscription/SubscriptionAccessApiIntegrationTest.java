package com.renovar.canteiro.io.platform.subscription;

import com.renovar.canteiro.io.AbstractPostgresIntegrationTest;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.access.domain.Permission;
import com.renovar.canteiro.io.access.domain.PermissionRepository;
import com.renovar.canteiro.io.access.domain.Role;
import com.renovar.canteiro.io.access.domain.RolePermission;
import com.renovar.canteiro.io.access.domain.RolePermissionRepository;
import com.renovar.canteiro.io.access.domain.RoleRepository;
import com.renovar.canteiro.io.access.domain.UserRole;
import com.renovar.canteiro.io.access.domain.UserRoleRepository;
import com.renovar.canteiro.io.identity.application.PasswordHasher;
import com.renovar.canteiro.io.identity.domain.CompanyUser;
import com.renovar.canteiro.io.identity.domain.CompanyUserRepository;
import com.renovar.canteiro.io.identity.domain.User;
import com.renovar.canteiro.io.identity.domain.UserRepository;
import com.renovar.canteiro.io.identity.domain.UserType;
import com.renovar.canteiro.io.platform.catalog.domain.CatalogPriceQuote;
import com.renovar.canteiro.io.platform.catalog.domain.CatalogPricingSource;
import com.renovar.canteiro.io.platform.company.domain.Company;
import com.renovar.canteiro.io.platform.company.domain.CompanyRepository;
import com.renovar.canteiro.io.platform.subscription.application.PaymentGatewayBillingMethod;
import com.renovar.canteiro.io.platform.subscription.domain.CompanySubscriptionAccess;
import com.renovar.canteiro.io.platform.subscription.domain.CompanySubscriptionAccessRepository;
import com.renovar.canteiro.io.platform.subscription.domain.PaymentGatewayProviderCode;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformCharge;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeRepository;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeStatus;
import com.renovar.canteiro.io.platform.subscription.domain.Subscription;
import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionAccessLevel;
import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SubscriptionAccessApiIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final PaymentGatewayProviderCode PROVIDER = new PaymentGatewayProviderCode("TEST_GATEWAY");
    private static final String PASSWORD = "Canteiro#2026Seguro";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyUserRepository companyUserRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private PlatformChargeRepository platformChargeRepository;

    @Autowired
    private CompanySubscriptionAccessRepository companySubscriptionAccessRepository;

    @Test
    void allowsReadsButRejectsMutationsWhenTheSubscriptionIsReadOnly() throws Exception {
        Company company = createCompany();
        User user = createCompanyUser(company);
        grantRolesRead(user, company);
        restrict(company, SubscriptionAccessLevel.READ_ONLY);

        mockMvc.perform(get("/api/v1/company/access/permissions")
                        .with(jwt().jwt(token -> token.subject(user.getId().toString()))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/company/access/roles")
                        .with(jwt().jwt(token -> token.subject(user.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Blocked write\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void rejectsReadsWhenTheSubscriptionIsBlocked() throws Exception {
        Company company = createCompany();
        User user = createCompanyUser(company);
        grantRolesRead(user, company);
        restrict(company, SubscriptionAccessLevel.BLOCKED);

        mockMvc.perform(get("/api/v1/company/access/permissions")
                        .with(jwt().jwt(token -> token.subject(user.getId().toString()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void doesNotApplyOneCompanysSubscriptionRestrictionToAnotherCompany() throws Exception {
        Company blockedCompany = createCompany();
        User blockedUser = createCompanyUser(blockedCompany);
        grantRolesRead(blockedUser, blockedCompany);
        restrict(blockedCompany, SubscriptionAccessLevel.BLOCKED);
        Company fullAccessCompany = createCompany();
        User fullAccessUser = createCompanyUser(fullAccessCompany);
        grantRolesRead(fullAccessUser, fullAccessCompany);

        mockMvc.perform(get("/api/v1/company/access/permissions")
                        .with(jwt().jwt(token -> token.subject(blockedUser.getId().toString()))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/company/access/permissions")
                        .with(jwt().jwt(token -> token.subject(fullAccessUser.getId().toString()))))
                .andExpect(status().isOk());
    }

    private Company createCompany() {
        return companyRepository.save(Company.create(
                "Construtora Access Ltda.", null, "DOC-" + UUID.randomUUID().toString().substring(0, 16),
                "access-" + UUID.randomUUID() + "@example.com", null, null, null
        ));
    }

    private User createCompanyUser(Company company) {
        User user = User.create("user-" + UUID.randomUUID() + "@example.com", UserType.COMPANY);
        user.activate(passwordHasher.hash(PASSWORD), Instant.now());
        User persistedUser = userRepository.save(user);
        companyUserRepository.save(CompanyUser.create(persistedUser.getId(), company.getId()));
        return persistedUser;
    }

    private void grantRolesRead(User user, Company company) {
        Role role = roleRepository.save(Role.create(company.getId(), "Subscription reader " + UUID.randomUUID(), null));
        Permission permission = permissionRepository.findByModuleAndAction(AccessModule.ROLES, AccessAction.READ)
                .orElseThrow();
        rolePermissionRepository.save(RolePermission.create(role.getId(), permission.getId()));
        userRoleRepository.save(UserRole.create(user.getId(), role.getId(), company.getId()));
    }

    private void restrict(Company company, SubscriptionAccessLevel accessLevel) {
        Subscription subscription = subscriptionRepository.save(Subscription.create(
                company.getId(),
                new CatalogPriceQuote(
                        Set.of(UUID.randomUUID()), new BigDecimal("99.90"), LocalDate.now(ZoneOffset.UTC),
                        CatalogPricingSource.INDIVIDUAL_PLANS, null
                )
        ));
        PlatformCharge charge = platformChargeRepository.save(PlatformCharge.create(
                company.getId(), subscription.getId(), PROVIDER, "access-key-" + UUID.randomUUID(),
                "cus_123", "pay_" + UUID.randomUUID(), PaymentGatewayBillingMethod.PIX,
                new BigDecimal("99.90"), LocalDate.now(ZoneOffset.UTC).minusDays(1), PlatformChargeStatus.PENDING
        ));
        companySubscriptionAccessRepository.save(CompanySubscriptionAccess.create(
                company.getId(), accessLevel, charge.getId(), LocalDate.now(ZoneOffset.UTC)
        ));
    }
}
