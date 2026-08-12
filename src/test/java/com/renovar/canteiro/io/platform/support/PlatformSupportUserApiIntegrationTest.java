package com.renovar.canteiro.io.platform.support;

import com.renovar.canteiro.io.AbstractPostgresIntegrationTest;
import com.renovar.canteiro.io.identity.application.AccountActivationEmailSender;
import com.renovar.canteiro.io.identity.application.PasswordHasher;
import com.renovar.canteiro.io.identity.domain.PlatformUser;
import com.renovar.canteiro.io.identity.domain.PlatformUserRepository;
import com.renovar.canteiro.io.identity.domain.PlatformUserRole;
import com.renovar.canteiro.io.identity.domain.User;
import com.renovar.canteiro.io.identity.domain.UserRepository;
import com.renovar.canteiro.io.identity.domain.UserType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Import(PlatformSupportUserApiIntegrationTest.AccountActivationEmailTestConfiguration.class)
class PlatformSupportUserApiIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String PASSWORD = "Canteiro#2026Seguro";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformUserRepository platformUserRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Test
    void platformOwnerCreatesAnActivatedSupportInvitation() throws Exception {
        User owner = createPlatformUser("owner@example.com", PlatformUserRole.PLATFORM_OWNER);

        mockMvc.perform(post("/api/v1/platform/support-users")
                        .with(jwt().jwt(token -> token.subject(owner.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"support@example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("support@example.com"))
                .andExpect(jsonPath("$.globalRole").value("PLATFORM_SUPPORT"))
                .andExpect(jsonPath("$.status").value("PENDING_ACTIVATION"));

        User supportUser = userRepository.findByEmail("support@example.com").orElseThrow();
        assertEquals(UserType.PLATFORM, supportUser.getUserType());
        assertEquals(PlatformUserRole.PLATFORM_SUPPORT, platformUserRepository.findByUserId(supportUser.getId())
                .orElseThrow()
                .getGlobalRole());
    }

    @Test
    void platformSupportCannotCreateAnotherPlatformSupportUser() throws Exception {
        User support = createPlatformUser("support-operator@example.com", PlatformUserRole.PLATFORM_SUPPORT);

        mockMvc.perform(post("/api/v1/platform/support-users")
                        .with(jwt().jwt(token -> token.subject(support.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"other-support@example.com\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertTrue(userRepository.findByEmail("other-support@example.com").isEmpty());
    }

    @Test
    void platformSupportCannotManageCompanyRolesThroughCompanyRoutes() throws Exception {
        User support = createPlatformUser("support-company-role@example.com", PlatformUserRole.PLATFORM_SUPPORT);

        mockMvc.perform(post("/api/v1/company/access/roles")
                        .with(jwt().jwt(token -> token.subject(support.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Unauthorized role\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    private User createPlatformUser(String email, PlatformUserRole globalRole) {
        User user = User.create(email, UserType.PLATFORM);
        user.activate(passwordHasher.hash(PASSWORD), Instant.now());
        User persistedUser = userRepository.save(user);
        platformUserRepository.save(PlatformUser.create(persistedUser.getId(), globalRole));
        return persistedUser;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AccountActivationEmailTestConfiguration {

        @Bean
        @Primary
        AccountActivationEmailSender accountActivationEmailSender() {
            return (email, rawActivationToken, expiresAt) -> { };
        }
    }
}
