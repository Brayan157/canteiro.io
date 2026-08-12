package com.renovar.canteiro.io.identity.infrastructure.email;

import com.renovar.canteiro.io.identity.application.PasswordResetEmailSender;
import com.renovar.canteiro.io.identity.application.PasswordResetProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class SmtpPasswordResetEmailSender implements PasswordResetEmailSender {

    private final ObjectProvider<JavaMailSender> javaMailSenderProvider;
    private final PasswordResetProperties passwordResetProperties;

    @Override
    public void send(String email, String rawPasswordResetToken, Instant expiresAt) {
        if (passwordResetProperties.emailFrom() == null || passwordResetProperties.emailFrom().isBlank()) {
            throw new IllegalStateException("Password reset email sender is not configured");
        }
        JavaMailSender javaMailSender = javaMailSenderProvider.getIfAvailable();
        if (javaMailSender == null) {
            throw new IllegalStateException("SMTP is not configured");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(passwordResetProperties.emailFrom());
        message.setTo(email);
        message.setSubject("Redefinição de senha - Canteiro.io");
        message.setText("Use o token abaixo para redefinir sua senha. Ele expira em " + expiresAt + ".\n\n"
                + rawPasswordResetToken);
        javaMailSender.send(message);
    }
}
