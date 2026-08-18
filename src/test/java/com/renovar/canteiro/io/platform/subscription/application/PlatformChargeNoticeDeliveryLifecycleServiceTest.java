package com.renovar.canteiro.io.platform.subscription.application;

import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.notifications.application.NotificationDeliveryProperties;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeNotice;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeNoticeRepository;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeNoticeStatus;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeNoticeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformChargeNoticeDeliveryLifecycleServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-22T12:00:00Z");

    @Mock
    private PlatformChargeNoticeRepository platformChargeNoticeRepository;
    @Mock
    private AuditEventRecorder auditEventRecorder;

    @Test
    void claimsPendingNoticesUsingTheConfiguredRetryWindow() {
        PlatformChargeNotice notice = notice();
        when(platformChargeNoticeRepository.claimPendingDeliveries(any(), any(), any(Integer.class)))
                .thenReturn(List.of(notice));

        List<PlatformChargeNotice> result = service().claimPendingDeliveries();

        assertEquals(List.of(notice), result);
        verify(platformChargeNoticeRepository).claimPendingDeliveries(NOW, NOW.minus(Duration.ofMinutes(15)), 25);
        verify(auditEventRecorder).recordSystemAction(
                eq(notice.getCompanyId()), eq(AuditModule.PLATFORM), eq(AuditAction.UPDATE),
                eq("PlatformChargeNotice"), eq(notice.getId()), eq(null), any(), any()
        );
    }

    @Test
    void marksDeliveryAsCompletedAndAuditsTheAutomaticChange() {
        PlatformChargeNotice notice = notice();
        notice.beginDelivery(NOW.minusSeconds(10));
        when(platformChargeNoticeRepository.findByIdForUpdate(notice.getId())).thenReturn(Optional.of(notice));
        when(platformChargeNoticeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service().markDelivered(notice.getId());

        ArgumentCaptor<PlatformChargeNotice> noticeCaptor = ArgumentCaptor.forClass(PlatformChargeNotice.class);
        verify(platformChargeNoticeRepository).save(noticeCaptor.capture());
        assertEquals(PlatformChargeNoticeStatus.DELIVERED, noticeCaptor.getValue().getStatus());
        verify(auditEventRecorder).recordSystemAction(
                eq(notice.getCompanyId()), eq(AuditModule.PLATFORM), eq(AuditAction.UPDATE),
                eq("PlatformChargeNotice"), eq(notice.getId()), any(), any(), any()
        );
    }

    private PlatformChargeNoticeDeliveryLifecycleService service() {
        return new PlatformChargeNoticeDeliveryLifecycleService(
                platformChargeNoticeRepository,
                new NotificationDeliveryProperties(25, Duration.ofMinutes(15)),
                auditEventRecorder,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private PlatformChargeNotice notice() {
        return PlatformChargeNotice.create(
                UUID.randomUUID(), UUID.randomUUID(), PlatformChargeNoticeType.DUE_DATE,
                "billing@example.com", LocalDate.of(2026, 8, 22)
        );
    }
}
