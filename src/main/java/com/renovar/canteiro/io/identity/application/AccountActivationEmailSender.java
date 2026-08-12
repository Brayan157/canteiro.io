package com.renovar.canteiro.io.identity.application;

import java.time.Instant;

public interface AccountActivationEmailSender {

    void send(String email, String rawActivationToken, Instant expiresAt);
}
