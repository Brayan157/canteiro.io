package com.renovar.canteiro.io.identity.infrastructure.email;

import com.renovar.canteiro.io.identity.application.AccountActivationEmailSender;
import com.renovar.canteiro.io.identity.application.AccountActivationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.nio.charset.StandardCharsets;

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

        String activationUrl = accountActivationProperties.frontendActivationUrl()
                + "#activationToken=" + rawActivationToken;
        try {
            MimeMessageHelper message = new MimeMessageHelper(
                    javaMailSender.createMimeMessage(),
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name()
            );
            message.setFrom(accountActivationProperties.emailFrom());
            message.setTo(email);
            message.setSubject("Ativação de conta - Canteiro.io");
            message.setText(
                    "Abra o link para ativar sua conta. Ele expira em " + expiresAt + ".\n\n" + activationUrl,
                    "<p>Olá,</p>"
                            + "<p>Para criar sua senha e ativar sua conta, clique no botão abaixo. "
                            + "O convite expira em " + expiresAt + ".</p>"
                            + "<p><a href=\"" + activationUrl + "\" "
                            + "style=\"display:inline-block;padding:12px 20px;background:#2563eb;color:#ffffff;"
                            + "text-decoration:none;border-radius:6px;font-weight:bold\">Ativar minha conta</a></p>"
                            + "<p>Se você não solicitou esta conta, ignore este e-mail.</p>"
            );
            javaMailSender.send(message.getMimeMessage());
        } catch (jakarta.mail.MessagingException exception) {
            throw new IllegalStateException("Could not create account activation email", exception);
        }
    }
}
