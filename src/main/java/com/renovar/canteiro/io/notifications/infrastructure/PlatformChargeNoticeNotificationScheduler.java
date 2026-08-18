package com.renovar.canteiro.io.notifications.infrastructure;

import com.renovar.canteiro.io.platform.subscription.application.PlatformChargeNoticeNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "notifications.delivery.enabled", havingValue = "true", matchIfMissing = true)
public class PlatformChargeNoticeNotificationScheduler {

    private final PlatformChargeNoticeNotificationService platformChargeNoticeNotificationService;

    @Scheduled(
            cron = "${notifications.delivery.schedule:0 */5 * * * *}",
            zone = "${notifications.delivery.zone:America/Sao_Paulo}"
    )
    public void deliverPendingNotices() {
        platformChargeNoticeNotificationService.deliverPendingNotices();
    }
}
