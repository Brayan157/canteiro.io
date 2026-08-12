package com.renovar.canteiro.io.identity.infrastructure.email;

import com.renovar.canteiro.io.identity.application.AccountActivationEmailSender;
import com.renovar.canteiro.io.identity.application.AccountActivationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class SmtpAccountActivationEmailSender implements AccountActivationEmailSender {

    private final ObjectProvider<JavaMailSender> javaMailSenderProvider;
    private final AccountActivationProperties accountActivationProperties;

    @Override
    public void send(String email, String rawActivationToken, Instant expiresAt) {
        if (accountActivationProperties.emailFrom() == null || accountActivationProperties.emailFrom().isBlank()) {
            throw new IllegalStateException("Account activation email sender is not configured");
        }
        JavaMailSender javaMailSender = javaMailSenderProvider.getIfAvailable();
        if (javaMailSender == null) {
            throw new IllegalStateException("SMTP is not configured");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(accountActivationProperties.emailFrom());
        message.setTo(email);
        message.setSubject("AtivaÃ§Ã£o de conta - Canteiro.io");
        message.setText("Use o token abaixo para ativar sua conta. Ele expira em " + expiresAt + ".\n\n"
                + rawActivationToken);
        javaMailSender.send(message);
    }
}
