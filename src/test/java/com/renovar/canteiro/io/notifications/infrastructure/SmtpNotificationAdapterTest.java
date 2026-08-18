package com.renovar.canteiro.io.notifications.infrastructure;

import com.renovar.canteiro.io.notifications.domain.EmailNotification;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmtpNotificationAdapterTest {

    @Test
    void mapsTheNeutralNotificationToAnSmtpMessage() {
        @SuppressWarnings("unchecked")
        ObjectProvider<JavaMailSender> senderProvider = mock(ObjectProvider.class);
        JavaMailSender sender = mock(JavaMailSender.class);
        when(senderProvider.getIfAvailable()).thenReturn(sender);
        SmtpNotificationAdapter adapter = new SmtpNotificationAdapter(
                senderProvider, new NotificationEmailProperties("support@canteiro.local")
        );

        adapter.send(new EmailNotification("billing@example.com", "Subject", "Body"));

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(messageCaptor.capture());
        assertEquals("support@canteiro.local", messageCaptor.getValue().getFrom());
        assertEquals("billing@example.com", messageCaptor.getValue().getTo()[0]);
        assertEquals("Subject", messageCaptor.getValue().getSubject());
    }
}
