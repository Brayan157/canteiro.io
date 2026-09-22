package com.renovar.canteiro.io.identity.api;

import com.renovar.canteiro.io.identity.api.request.ActivateAccountRequest;
import com.renovar.canteiro.io.identity.api.request.LoginRequest;
import com.renovar.canteiro.io.identity.api.request.PasswordResetConfirmationRequest;
import com.renovar.canteiro.io.identity.api.request.PasswordResetRequest;
import com.renovar.canteiro.io.identity.api.request.ResendAccountActivationRequest;
import com.renovar.canteiro.io.identity.api.response.AuthenticationTokensResponse;
import com.renovar.canteiro.io.identity.api.response.AuthenticatedUserResponse;
import com.renovar.canteiro.io.identity.application.ActivateAccountCommand;
import com.renovar.canteiro.io.identity.application.ActivateAccountService;
import com.renovar.canteiro.io.identity.application.AuthenticatedUserQueryService;
import com.renovar.canteiro.io.identity.application.AuthenticationService;
import com.renovar.canteiro.io.identity.application.AuthenticationTokens;
import com.renovar.canteiro.io.identity.application.InvalidAuthenticationException;
import com.renovar.canteiro.io.identity.application.LoginCommand;
import com.renovar.canteiro.io.identity.application.LogoutCommand;
import com.renovar.canteiro.io.identity.application.RefreshSessionCommand;
import com.renovar.canteiro.io.identity.application.RefreshCookieProperties;
import com.renovar.canteiro.io.identity.application.PasswordResetService;
import com.renovar.canteiro.io.identity.application.RequestPasswordResetCommand;
import com.renovar.canteiro.io.identity.application.ResetPasswordCommand;
import com.renovar.canteiro.io.identity.application.ResendAccountActivationCommand;
import com.renovar.canteiro.io.identity.application.ResendAccountActivationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/auth")
@SecurityRequirements
@Tag(name = "Authentication")
@RequiredArgsConstructor
public class AuthenticationController {

    private final ActivateAccountService activateAccountService;
    private final ResendAccountActivationService resendAccountActivationService;
    private final AuthenticationService authenticationService;
    private final PasswordResetService passwordResetService;
    private final AuthenticatedUserQueryService authenticatedUserQueryService;
    private final RefreshCookieProperties refreshCookieProperties;

    @GetMapping("/me")
    @Operation(summary = "Returns the authenticated user, context and effective access")
    public AuthenticatedUserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return authenticatedUserQueryService.currentUser(java.util.UUID.fromString(jwt.getSubject()));
    }

    @PostMapping("/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Activates a pending account with a one-time token")
    public void activate(@Valid @RequestBody ActivateAccountRequest request) {
        activateAccountService.activate(new ActivateAccountCommand(request.activationToken(), request.password()));
    }

    @PostMapping("/activation/resend")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Resends an activation link without disclosing account existence")
    public void resendActivation(@Valid @RequestBody ResendAccountActivationRequest request) {
        resendAccountActivationService.resend(new ResendAccountActivationCommand(request.email()));
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticates an active user and creates an access session")
    public ResponseEntity<AuthenticationTokensResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthenticationTokens tokens = authenticationService.login(new LoginCommand(
                request.email(),
                request.password(),
                request.rememberAccess()
        ));
        return sessionResponse(tokens);
    }

    @PostMapping(value = "/refresh", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Rotates a refresh token and returns a new session")
    public ResponseEntity<AuthenticationTokensResponse> refresh(HttpServletRequest request) {
        AuthenticationTokens tokens = authenticationService.refresh(new RefreshSessionCommand(
                requireRefreshToken(request)
        ));
        return sessionResponse(tokens);
    }

    @PostMapping(value = "/logout", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Revokes a refresh token")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        String refreshToken = findRefreshToken(request);
        if (refreshToken != null) {
            authenticationService.logout(new LogoutCommand(refreshToken));
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expiredRefreshCookie().toString())
                .build();
    }

    @PostMapping("/password-reset/request")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Requests a password reset email without disclosing account existence")
    public void requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.requestReset(new RequestPasswordResetCommand(request.email()));
    }

    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Resets a password with a one-time token and revokes active sessions")
    public void confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmationRequest request) {
        passwordResetService.resetPassword(new ResetPasswordCommand(request.passwordResetToken(), request.newPassword()));
    }

    private AuthenticationTokensResponse toResponse(AuthenticationTokens authenticationTokens) {
        return new AuthenticationTokensResponse(
                authenticationTokens.accessToken(),
                "Bearer",
                authenticationTokens.accessTokenExpiresInSeconds()
        );
    }

    private ResponseEntity<AuthenticationTokensResponse> sessionResponse(AuthenticationTokens tokens) {
        ResponseCookie.ResponseCookieBuilder cookie = ResponseCookie
                .from(refreshCookieProperties.name(), tokens.refreshToken())
                .httpOnly(true)
                .secure(refreshCookieProperties.secure())
                .sameSite(refreshCookieProperties.sameSite())
                .path(refreshCookieProperties.path());

        if (tokens.rememberAccess()) {
            cookie.maxAge(Duration.ofSeconds(tokens.refreshTokenExpiresInSeconds()));
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.build().toString())
                .body(toResponse(tokens));
    }

    private ResponseCookie expiredRefreshCookie() {
        return ResponseCookie.from(refreshCookieProperties.name(), "")
                .httpOnly(true)
                .secure(refreshCookieProperties.secure())
                .sameSite(refreshCookieProperties.sameSite())
                .path(refreshCookieProperties.path())
                .maxAge(Duration.ZERO)
                .build();
    }

    private String requireRefreshToken(HttpServletRequest request) {
        String refreshToken = findRefreshToken(request);
        if (refreshToken == null) {
            throw new InvalidAuthenticationException("Refresh token is invalid or expired");
        }
        return refreshToken;
    }

    private String findRefreshToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (refreshCookieProperties.name().equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
