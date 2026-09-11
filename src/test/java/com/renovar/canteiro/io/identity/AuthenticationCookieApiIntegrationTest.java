package com.renovar.canteiro.io.identity;

import com.renovar.canteiro.io.AbstractPostgresIntegrationTest;
import com.renovar.canteiro.io.identity.application.PasswordHasher;
import com.renovar.canteiro.io.identity.domain.User;
import com.renovar.canteiro.io.identity.domain.UserRepository;
import com.renovar.canteiro.io.identity.domain.UserType;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuthenticationCookieApiIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String PASSWORD = "Canteiro#2026Seguro";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Test
    void remembersAccessInHttpOnlyCookieAndRotatesItWithoutReturningRefreshToken() throws Exception {
        createActiveUser("remember-cookie@example.com");

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "remember-cookie@example.com",
                                  "password": "Canteiro#2026Seguro",
                                  "rememberAccess": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        Cookie firstCookie = requireRefreshCookie(login);
        assertTrue(firstCookie.isHttpOnly());
        assertFalse(firstCookie.getSecure());
        assertEquals(30 * 24 * 60 * 60, firstCookie.getMaxAge());
        assertTrue(login.getResponse().getHeader("Set-Cookie").contains("SameSite=Strict"));

        MvcResult refresh = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(firstCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        Cookie rotatedCookie = requireRefreshCookie(refresh);
        assertNotEquals(firstCookie.getValue(), rotatedCookie.getValue());
        assertEquals(firstCookie.getMaxAge(), rotatedCookie.getMaxAge());

        MvcResult logout = mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(rotatedCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNoContent())
                .andReturn();

        assertEquals(0, requireRefreshCookie(logout).getMaxAge());
    }

    @Test
    void usesSessionCookieWhenRememberAccessIsFalse() throws Exception {
        createActiveUser("session-cookie@example.com");

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "session-cookie@example.com",
                                  "password": "Canteiro#2026Seguro",
                                  "rememberAccess": false
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        assertEquals(-1, requireRefreshCookie(login).getMaxAge());
    }

    private Cookie requireRefreshCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie("canteiro_refresh");
        assertNotNull(cookie);
        return cookie;
    }

    private void createActiveUser(String email) {
        User user = User.create(email, UserType.COMPANY);
        user.activate(passwordHasher.hash(PASSWORD), Instant.now());
        userRepository.save(user);
    }
}
