package com.renovar.canteiro.io.identity.infrastructure.email;

import com.renovar.canteiro.io.identity.application.AccountActivationProperties;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SmtpAccountActivationEmailSenderTest {

    @Test
    void sendsActivationAsAnEmailButtonAndKeepsTheTokenInTheUrlFragment() throws Exception {
        JavaMailSender javaMailSender = mock(JavaMailSender.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<JavaMailSender> senderProvider = mock(ObjectProvider.class);
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(senderProvider.getIfAvailable()).thenReturn(javaMailSender);
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        SmtpAccountActivationEmailSender sender = new SmtpAccountActivationEmailSender(
                senderProvider,
                new AccountActivationProperties(
                        Duration.ofHours(24),
                        "no-reply@canteiro.local",
                        "http://localhost:5173/ativar-conta"
                )
        );

        sender.send("owner@example.com", "url-safe-token_123", Instant.parse("2026-09-04T12:00:00Z"));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        mimeMessage.writeTo(output);
        String rawMessage = output.toString(StandardCharsets.UTF_8);
        assertTrue(rawMessage.contains("Ativar minha conta"));
        assertTrue(rawMessage.contains("http://localhost:5173/ativar-conta#activationToken=url-safe-token_123"));
        assertFalse(rawMessage.contains("Use o token abaixo"));
    }
}
