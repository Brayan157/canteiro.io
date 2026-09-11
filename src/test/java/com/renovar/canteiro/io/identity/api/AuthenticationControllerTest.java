package com.renovar.canteiro.io.identity.api;

import com.renovar.canteiro.io.identity.api.request.LoginRequest;
import com.renovar.canteiro.io.identity.api.request.ResendAccountActivationRequest;
import com.renovar.canteiro.io.identity.api.response.AuthenticationTokensResponse;
import com.renovar.canteiro.io.identity.application.ActivateAccountService;
import com.renovar.canteiro.io.identity.application.AuthenticatedUserQueryService;
import com.renovar.canteiro.io.identity.application.AuthenticationService;
import com.renovar.canteiro.io.identity.application.AuthenticationTokens;
import com.renovar.canteiro.io.identity.application.LoginCommand;
import com.renovar.canteiro.io.identity.application.PasswordResetService;
import com.renovar.canteiro.io.identity.application.RefreshCookieProperties;
import com.renovar.canteiro.io.identity.application.ResendAccountActivationService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthenticationControllerTest {

    @Test
    void returnsOnlyAccessTokenAndCreatesPersistentSecureCookieWhenRemembered() {
        AuthenticationService service = mock(AuthenticationService.class);
        when(service.login(any(LoginCommand.class))).thenReturn(tokens(true));
        AuthenticationController controller = controller(service);

        ResponseEntity<AuthenticationTokensResponse> response = controller.login(new LoginRequest(
                "user@example.com",
                "Canteiro#2026Seguro",
                true
        ));

        AuthenticationTokensResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("access-token", body.accessToken());
        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie);
        assertTrue(setCookie.contains("canteiro_refresh=raw-refresh-token"));
        assertTrue(setCookie.contains("Max-Age=2592000"));
        assertTrue(setCookie.contains("Path=/api/v1/auth"));
        assertTrue(setCookie.contains("Secure"));
        assertTrue(setCookie.contains("HttpOnly"));
        assertTrue(setCookie.contains("SameSite=Strict"));
    }

    @Test
    void rotatesFromCookieAsSessionCookieAndExpiresItOnLogout() {
        AuthenticationService service = mock(AuthenticationService.class);
        when(service.refresh(any())).thenReturn(tokens(false));
        AuthenticationController controller = controller(service);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("canteiro_refresh", "current-refresh")});

        ResponseEntity<AuthenticationTokensResponse> refresh = controller.refresh(request);

        String refreshCookie = refresh.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertNotNull(refreshCookie);
        assertFalse(refreshCookie.contains("Max-Age="));

        ResponseEntity<Void> logout = controller.logout(request);
        String logoutCookie = logout.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertNotNull(logoutCookie);
        assertTrue(logoutCookie.contains("Max-Age=0"));
        verify(service).logout(any());
    }

    @Test
    void delegatesActivationLinkResendWithoutReturningAnyAccountData() {
        ResendAccountActivationService resendService = mock(ResendAccountActivationService.class);
        AuthenticationController controller = controller(mock(AuthenticationService.class), resendService);

        controller.resendActivation(new ResendAccountActivationRequest("pending@example.com"));

        verify(resendService).resend(any());
    }

    private AuthenticationController controller(AuthenticationService service) {
        return controller(service, mock(ResendAccountActivationService.class));
    }

    private AuthenticationController controller(
            AuthenticationService service,
            ResendAccountActivationService resendAccountActivationService
    ) {
        return new AuthenticationController(
                mock(ActivateAccountService.class),
                resendAccountActivationService,
                service,
                mock(PasswordResetService.class),
                mock(AuthenticatedUserQueryService.class),
                new RefreshCookieProperties("canteiro_refresh", "/api/v1/auth", true, "Strict")
        );
    }

    private AuthenticationTokens tokens(boolean rememberAccess) {
        return new AuthenticationTokens(
                "access-token",
                "raw-refresh-token",
                900,
                30L * 24 * 60 * 60,
                rememberAccess
        );
    }
}
