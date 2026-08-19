package com.renovar.canteiro.io.identity.api;

import com.renovar.canteiro.io.identity.api.request.ActivateAccountRequest;
import com.renovar.canteiro.io.identity.api.request.LoginRequest;
import com.renovar.canteiro.io.identity.api.request.PasswordResetConfirmationRequest;
import com.renovar.canteiro.io.identity.api.request.PasswordResetRequest;
import com.renovar.canteiro.io.identity.api.request.RefreshTokenRequest;
import com.renovar.canteiro.io.identity.api.response.AuthenticationTokensResponse;
import com.renovar.canteiro.io.identity.application.ActivateAccountCommand;
import com.renovar.canteiro.io.identity.application.ActivateAccountService;
import com.renovar.canteiro.io.identity.application.AuthenticationService;
import com.renovar.canteiro.io.identity.application.AuthenticationTokens;
import com.renovar.canteiro.io.identity.application.LoginCommand;
import com.renovar.canteiro.io.identity.application.LogoutCommand;
import com.renovar.canteiro.io.identity.application.RefreshSessionCommand;
import com.renovar.canteiro.io.identity.application.PasswordResetService;
import com.renovar.canteiro.io.identity.application.RequestPasswordResetCommand;
import com.renovar.canteiro.io.identity.application.ResetPasswordCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@SecurityRequirements
@Tag(name = "Authentication")
@RequiredArgsConstructor
public class AuthenticationController {

    private final ActivateAccountService activateAccountService;
    private final AuthenticationService authenticationService;
    private final PasswordResetService passwordResetService;

    @PostMapping("/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Activates a pending account with a one-time token")
    public void activate(@Valid @RequestBody ActivateAccountRequest request) {
        activateAccountService.activate(new ActivateAccountCommand(request.activationToken(), request.password()));
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticates an active user and creates an access session")
    public AuthenticationTokensResponse login(@Valid @RequestBody LoginRequest request) {
        return toResponse(authenticationService.login(new LoginCommand(request.email(), request.password())));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotates a refresh token and returns a new session")
    public AuthenticationTokensResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return toResponse(authenticationService.refresh(new RefreshSessionCommand(request.refreshToken())));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revokes a refresh token")
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        authenticationService.logout(new LogoutCommand(request.refreshToken()));
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
                authenticationTokens.refreshToken(),
                "Bearer",
                authenticationTokens.accessTokenExpiresInSeconds()
        );
    }
}
