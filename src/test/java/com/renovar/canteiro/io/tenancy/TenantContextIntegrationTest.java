package com.renovar.canteiro.io.tenancy;

import com.renovar.canteiro.io.AbstractPostgresIntegrationTest;
import com.renovar.canteiro.io.identity.application.PasswordHasher;
import com.renovar.canteiro.io.identity.domain.CompanyUser;
import com.renovar.canteiro.io.identity.domain.CompanyUserRepository;
import com.renovar.canteiro.io.identity.domain.User;
import com.renovar.canteiro.io.identity.domain.UserRepository;
import com.renovar.canteiro.io.identity.domain.UserType;
import com.renovar.canteiro.io.platform.company.domain.Company;
import com.renovar.canteiro.io.platform.company.domain.CompanyRepository;
import com.renovar.canteiro.io.tenancy.application.TenantAuthenticationException;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import com.renovar.canteiro.io.tenancy.application.TenantContextResolver;
import com.renovar.canteiro.io.tenancy.infrastructure.TenantContextAuthenticationFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantContextIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String PASSWORD = "Canteiro#2026Seguro";

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyUserRepository companyUserRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private TenantContextResolver tenantContextResolver;

    @Autowired
    private TenantContextHolder tenantContextHolder;

    @Autowired
    private TenantContextAuthenticationFilter tenantContextAuthenticationFilter;

    @AfterEach
    void cleanSecurityState() {
        SecurityContextHolder.clearContext();
        tenantContextHolder.clear();
    }

    @Test
    void resolvesCompanyOnlyFromTheAuthenticatedUsersCompanyLink() {
        Company firstCompany = createCompany("First");
        Company secondCompany = createCompany("Second");
        User user = createActiveUser("tenant@example.com", UserType.COMPANY);
        companyUserRepository.save(CompanyUser.create(user.getId(), firstCompany.getId()));

        var tenantContext = tenantContextResolver.resolve(user.getId()).orElseThrow();

        assertEquals(user.getId(), tenantContext.userId());
        assertEquals(firstCompany.getId(), tenantContext.companyId());
        assertFalse(secondCompany.getId().equals(tenantContext.companyId()));
    }

    @Test
    void keepsTenantContextOnlyForTheAuthenticatedRequest() throws Exception {
        Company company = createCompany("Request");
        User user = createActiveUser("request-tenant@example.com", UserType.COMPANY);
        companyUserRepository.save(CompanyUser.create(user.getId(), company.getId()));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwtFor(user.getId()),
                List.of(new SimpleGrantedAuthority("ROLE_COMPANY_USER"))
        ));

        FilterChain filterChain = (request, response) -> assertEquals(
                company.getId(),
                tenantContextHolder.requireCurrentTenant().companyId()
        );
        tenantContextAuthenticationFilter.doFilter(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                filterChain
        );

        assertTrue(tenantContextHolder.currentTenant().isEmpty());
    }

    @Test
    void rejectsCompanyUserWithoutCompanyLink() {
        User user = createActiveUser("missing-link@example.com", UserType.COMPANY);

        assertThrows(TenantAuthenticationException.class, () -> tenantContextResolver.resolve(user.getId()));
    }

    @Test
    void returnsUnauthenticatedProblemForCompanyUserWithoutCompanyLink() throws Exception {
        User user = createActiveUser("missing-filter-link@example.com", UserType.COMPANY);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwtFor(user.getId()),
                List.of(new SimpleGrantedAuthority("ROLE_COMPANY_USER"))
        ));
        MockHttpServletResponse response = new MockHttpServletResponse();

        tenantContextAuthenticationFilter.doFilter(new MockHttpServletRequest(), response, (request, ignored) -> {
            throw new AssertionError("The protected request must not reach its endpoint");
        });

        assertEquals(401, response.getStatus());
        assertEquals("application/problem+json", response.getContentType());
        assertTrue(response.getContentAsString().contains("UNAUTHENTICATED"));
        assertTrue(tenantContextHolder.currentTenant().isEmpty());
    }

    @Test
    void doesNotCreateCompanyTenantForPlatformUsers() {
        User platformUser = createActiveUser("platform@example.com", UserType.PLATFORM);

        assertTrue(tenantContextResolver.resolve(platformUser.getId()).isEmpty());
    }

    @Test
    void rejectsPlatformUsersFromCompanyRoutes() throws Exception {
        User platformUser = createActiveUser("platform-company-route@example.com", UserType.PLATFORM);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwtFor(platformUser.getId()),
                List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_SUPPORT"))
        ));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/company/access/employees");
        MockHttpServletResponse response = new MockHttpServletResponse();

        tenantContextAuthenticationFilter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
            throw new AssertionError("A platform user must not enter a company route");
        });

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("ACCESS_DENIED"));
    }

    private Company createCompany(String suffix) {
        return companyRepository.save(Company.create(
                suffix + " Company Ltda.",
                suffix + " Company",
                Long.toUnsignedString(UUID.randomUUID().getLeastSignificantBits()),
                suffix.toLowerCase() + "@example.com",
                null,
                null,
                null
        ));
    }

    private User createActiveUser(String email, UserType userType) {
        User user = User.create(email, userType);
        user.activate(passwordHasher.hash(PASSWORD), Instant.now());
        return userRepository.save(user);
    }

    private Jwt jwtFor(UUID userId) {
        Instant now = Instant.now();
        return new Jwt(
                "token-value",
                now,
                now.plusSeconds(60),
                Map.of("alg", "HS256"),
                Map.of("sub", userId.toString())
        );
    }
}
