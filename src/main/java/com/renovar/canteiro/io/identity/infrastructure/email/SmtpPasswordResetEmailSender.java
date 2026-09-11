package com.renovar.canteiro.io.identity.infrastructure.email;

import com.renovar.canteiro.io.identity.application.PasswordResetEmailSender;
import com.renovar.canteiro.io.identity.application.PasswordResetProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
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

        String passwordResetUrl = passwordResetProperties.frontendPasswordResetUrl()
                + "#passwordResetToken=" + rawPasswordResetToken;
        try {
            MimeMessageHelper message = new MimeMessageHelper(
                    javaMailSender.createMimeMessage(),
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name()
            );
            message.setFrom(passwordResetProperties.emailFrom());
            message.setTo(email);
            message.setSubject("Redefinição de senha - Canteiro.io");
            message.setText(
                    "Abra o link para redefinir sua senha. Ele expira em " + expiresAt + ".\n\n" + passwordResetUrl,
                    "<p>Olá,</p>"
                            + "<p>Para redefinir sua senha, clique no botão abaixo. "
                            + "O link expira em " + expiresAt + ".</p>"
                            + "<p><a href=\"" + passwordResetUrl + "\" "
                            + "style=\"display:inline-block;padding:12px 20px;background:#2563eb;color:#ffffff;"
                            + "text-decoration:none;border-radius:6px;font-weight:bold\">Redefinir minha senha</a></p>"
                            + "<p>Se você não solicitou esta redefinição, ignore este e-mail.</p>"
            );
            javaMailSender.send(message.getMimeMessage());
        } catch (jakarta.mail.MessagingException exception) {
            throw new IllegalStateException("Could not create password reset email", exception);
        }
    }
}
