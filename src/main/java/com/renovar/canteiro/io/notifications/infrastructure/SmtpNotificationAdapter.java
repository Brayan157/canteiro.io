package com.renovar.canteiro.io.notifications.infrastructure;

import com.renovar.canteiro.io.notifications.application.NotificationPort;
import com.renovar.canteiro.io.notifications.domain.EmailNotification;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SmtpNotificationAdapter implements NotificationPort {

    private final ObjectProvider<JavaMailSender> javaMailSenderProvider;
    private final NotificationEmailProperties notificationEmailProperties;

    @Override
    public void send(EmailNotification notification) {
        if (notificationEmailProperties.from() == null || notificationEmailProperties.from().isBlank()) {
            throw new IllegalStateException("Notification email sender is not configured");
        }
        JavaMailSender javaMailSender = javaMailSenderProvider.getIfAvailable();
        if (javaMailSender == null) {
            throw new IllegalStateException("SMTP is not configured");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(notificationEmailProperties.from());
        message.setTo(notification.recipient());
        message.setSubject(notification.subject());
        message.setText(notification.content());
        javaMailSender.send(message);
    }
}
