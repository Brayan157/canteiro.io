package com.renovar.canteiro.io.notifications.application;

import com.renovar.canteiro.io.notifications.domain.EmailNotification;

public interface NotificationPort {

    void send(EmailNotification notification);
}
