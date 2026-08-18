package com.renovar.canteiro.io.platform.subscription.application;

import com.renovar.canteiro.io.notifications.application.NotificationPort;
import com.renovar.canteiro.io.notifications.domain.EmailNotification;
import com.renovar.canteiro.io.platform.subscription.domain.PaymentGatewayProviderCode;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformCharge;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeNotice;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeNoticeType;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeRepository;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformChargeNoticeNotificationServiceTest {

    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID CHARGE_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-08-22T12:00:00Z");

    @Mock
    private PlatformChargeNoticeDeliveryLifecycleService deliveryLifecycleService;
    @Mock
    private PlatformChargeRepository platformChargeRepository;
    @Mock
    private NotificationPort notificationPort;

    @Test
    void sendsAnUnpaidNoticeAndMarksItDelivered() {
        PlatformChargeNotice notice = notice(PlatformChargeNoticeType.READ_ONLY);
        when(deliveryLifecycleService.claimPendingDeliveries()).thenReturn(List.of(notice));
        when(platformChargeRepository.findById(CHARGE_ID)).thenReturn(Optional.of(unpaidCharge()));

        NotificationDeliveryRunResult result = service().deliverPendingNotices();

        assertEquals(new NotificationDeliveryRunResult(1, 1, 0, 0), result);
        ArgumentCaptor<EmailNotification> notificationCaptor = ArgumentCaptor.forClass(EmailNotification.class);
        verify(notificationPort).send(notificationCaptor.capture());
        assertEquals("billing@example.com", notificationCaptor.getValue().recipient());
        assertEquals("Canteiro.io - acesso em modo consulta", notificationCaptor.getValue().subject());
        verify(deliveryLifecycleService).markDelivered(notice.getId());
    }

    @Test
    void cancelsTheNoticeWhenItsChargeWasAlreadyPaid() {
        PlatformChargeNotice notice = notice(PlatformChargeNoticeType.DUE_DATE);
        when(deliveryLifecycleService.claimPendingDeliveries()).thenReturn(List.of(notice));
        when(platformChargeRepository.findById(CHARGE_ID)).thenReturn(Optional.of(paidCharge()));

        NotificationDeliveryRunResult result = service().deliverPendingNotices();

        assertEquals(new NotificationDeliveryRunResult(1, 0, 0, 1), result);
        verify(deliveryLifecycleService).cancel(notice.getId());
        verify(notificationPort, never()).send(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void recordsAFailedDeliveryWithoutLeakingTheProviderMessage() {
        PlatformChargeNotice notice = notice(PlatformChargeNoticeType.BLOCKED);
        when(deliveryLifecycleService.claimPendingDeliveries()).thenReturn(List.of(notice));
        when(platformChargeRepository.findById(CHARGE_ID)).thenReturn(Optional.of(unpaidCharge()));
        org.mockito.Mockito.doThrow(new IllegalStateException("smtp-password=secret"))
                .when(notificationPort).send(org.mockito.ArgumentMatchers.any());

        NotificationDeliveryRunResult result = service().deliverPendingNotices();

        assertEquals(new NotificationDeliveryRunResult(1, 0, 1, 0), result);
        verify(deliveryLifecycleService).markFailed(notice.getId(), "IllegalStateException");
    }

    private PlatformChargeNoticeNotificationService service() {
        return new PlatformChargeNoticeNotificationService(
                deliveryLifecycleService,
                platformChargeRepository,
                notificationPort,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private PlatformChargeNotice notice(PlatformChargeNoticeType noticeType) {
        return PlatformChargeNotice.create(COMPANY_ID, CHARGE_ID, noticeType, "billing@example.com", NOW.atZone(ZoneOffset.UTC).toLocalDate());
    }

    private PlatformCharge unpaidCharge() {
        return charge(PlatformChargeStatus.PENDING);
    }

    private PlatformCharge paidCharge() {
        return charge(PlatformChargeStatus.CONFIRMED);
    }

    private PlatformCharge charge(PlatformChargeStatus status) {
        return PlatformCharge.rehydrate(
                CHARGE_ID, COMPANY_ID, UUID.randomUUID(), new PaymentGatewayProviderCode("TEST_GATEWAY"), "charge-key",
                "cus_123", "pay_123", PaymentGatewayBillingMethod.PIX, new BigDecimal("99.90"),
                LocalDate.of(2026, 8, 22), status, null, null
        );
    }
}
