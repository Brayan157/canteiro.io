package com.renovar.canteiro.io.platform.support;

import com.renovar.canteiro.io.AbstractPostgresIntegrationTest;
import com.renovar.canteiro.io.identity.application.PasswordHasher;
import com.renovar.canteiro.io.identity.domain.PlatformUser;
import com.renovar.canteiro.io.identity.domain.PlatformUserRepository;
import com.renovar.canteiro.io.identity.domain.PlatformUserRole;
import com.renovar.canteiro.io.identity.domain.User;
import com.renovar.canteiro.io.identity.domain.UserRepository;
import com.renovar.canteiro.io.identity.domain.UserType;
import com.renovar.canteiro.io.platform.company.domain.Company;
import com.renovar.canteiro.io.platform.company.domain.CompanyRepository;
import com.renovar.canteiro.io.platform.support.application.PlatformOperatorContextHolder;
import com.renovar.canteiro.io.platform.support.application.SupportTargetContextHolder;
import com.renovar.canteiro.io.platform.support.infrastructure.PlatformOperatorAuthenticationFilter;
import com.renovar.canteiro.io.platform.support.infrastructure.SupportTargetContextFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupportTargetContextIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String PASSWORD = "Canteiro#2026Seguro";

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformUserRepository platformUserRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private PlatformOperatorAuthenticationFilter platformOperatorAuthenticationFilter;

    @Autowired
    private SupportTargetContextFilter supportTargetContextFilter;

    @Autowired
    private PlatformOperatorContextHolder platformOperatorContextHolder;

    @Autowired
    private SupportTargetContextHolder supportTargetContextHolder;

    @AfterEach
    void clearSecurityContexts() {
        SecurityContextHolder.clearContext();
        platformOperatorContextHolder.clear();
        supportTargetContextHolder.clear();
    }

    @Test
    void resolvesTheTargetCompanyOnlyForAnAuthenticatedPlatformSupportOperator() throws Exception {
        Company company = createCompany("Support target");
        User supportUser = createPlatformSupportUser("support-target@example.com");
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwtFor(supportUser.getId()),
                List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_SUPPORT"))
        ));
        MockHttpServletRequest request = supportRequest(company.getId());

        platformOperatorAuthenticationFilter.doFilter(request, new MockHttpServletResponse(), (operatorRequest, operatorResponse) ->
                supportTargetContextFilter.doFilter(operatorRequest, operatorResponse, (targetRequest, targetResponse) -> {
                    var targetContext = supportTargetContextHolder.requireCurrentTarget();
                    assertEquals(supportUser.getId(), targetContext.operatorUserId());
                    assertEquals(company.getId(), targetContext.targetCompanyId());
                })
        );

        assertTrue(platformOperatorContextHolder.currentOperator().isEmpty());
        assertTrue(supportTargetContextHolder.currentTarget().isEmpty());
    }

    @Test
    void rejectsTargetCompanySelectionForACompanyUser() throws Exception {
        Company company = createCompany("Company user target");
        User companyUser = createActiveUser("company-user@example.com", UserType.COMPANY);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwtFor(companyUser.getId()),
                List.of(new SimpleGrantedAuthority("ROLE_COMPANY_USER"))
        ));
        MockHttpServletResponse response = new MockHttpServletResponse();

        platformOperatorAuthenticationFilter.doFilter(supportRequest(company.getId()), response, (operatorRequest, operatorResponse) ->
                supportTargetContextFilter.doFilter(operatorRequest, operatorResponse, (targetRequest, targetResponse) -> {
                    throw new AssertionError("A company user must not enter a platform support target context");
                })
        );

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("ACCESS_DENIED"));
    }

    private MockHttpServletRequest supportRequest(UUID targetCompanyId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/platform/support/reports");
        request.addHeader(SupportTargetContextFilter.TARGET_COMPANY_ID_HEADER, targetCompanyId.toString());
        return request;
    }

    private Company createCompany(String suffix) {
        return companyRepository.save(Company.create(
                suffix + " Ltda.",
                suffix,
                Long.toUnsignedString(UUID.randomUUID().getLeastSignificantBits()),
                UUID.randomUUID() + "@example.com",
                null,
                null,
                null
        ));
    }

    private User createPlatformSupportUser(String email) {
        User user = createActiveUser(email, UserType.PLATFORM);
        platformUserRepository.save(PlatformUser.create(user.getId(), PlatformUserRole.PLATFORM_SUPPORT));
        return user;
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
