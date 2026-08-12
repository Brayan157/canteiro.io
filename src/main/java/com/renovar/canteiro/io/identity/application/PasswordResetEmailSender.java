package com.renovar.canteiro.io.identity.application;

import java.time.Instant;

public interface PasswordResetEmailSender {

    void send(String email, String rawPasswordResetToken, Instant expiresAt);
}
