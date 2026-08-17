package com.renovar.canteiro.io.platform.subscription.application;

import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.notifications.application.NotificationDeliveryProperties;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeNotice;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeNoticeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlatformChargeNoticeDeliveryLifecycleService {

    private static final String ORIGIN = "subscription-notification-delivery";

    private final PlatformChargeNoticeRepository platformChargeNoticeRepository;
    private final NotificationDeliveryProperties notificationDeliveryProperties;
    private final AuditEventRecorder auditEventRecorder;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<PlatformChargeNotice> claimPendingDeliveries() {
        Instant now = clock.instant();
        List<PlatformChargeNotice> claimed = platformChargeNoticeRepository.claimPendingDeliveries(
                now, now.minus(notificationDeliveryProperties.retryAfter()), notificationDeliveryProperties.batchSize()
        );
        claimed.forEach(this::auditDeliveryClaim);
        return claimed;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markDelivered(UUID noticeId) {
        update(noticeId, notice -> notice.markDelivered(clock.instant()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID noticeId, String reason) {
        update(noticeId, notice -> notice.markDeliveryFailed(reason));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancel(UUID noticeId) {
        update(noticeId, PlatformChargeNotice::cancel);
    }

    private void update(UUID noticeId, NoticeStateChange stateChange) {
        PlatformChargeNotice notice = platformChargeNoticeRepository.findByIdForUpdate(noticeId)
                .orElseThrow(() -> new IllegalArgumentException("Platform charge notice was not found"));
        Map<String, Object> beforeData = auditData(notice);
        stateChange.apply(notice);
        PlatformChargeNotice saved = platformChargeNoticeRepository.save(notice);
        auditEventRecorder.recordSystemAction(
                saved.getCompanyId(),
                AuditModule.PLATFORM,
                AuditAction.UPDATE,
                "PlatformChargeNotice",
                saved.getId(),
                beforeData,
                auditData(saved),
                Map.of("origin", ORIGIN)
        );
    }

    private void auditDeliveryClaim(PlatformChargeNotice notice) {
        auditEventRecorder.recordSystemAction(
                notice.getCompanyId(),
                AuditModule.PLATFORM,
                AuditAction.UPDATE,
                "PlatformChargeNotice",
                notice.getId(),
                null,
                auditData(notice),
                Map.of("origin", ORIGIN, "stage", "claim")
        );
    }

    private Map<String, Object> auditData(PlatformChargeNotice notice) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("chargeId", notice.getChargeId());
        data.put("noticeType", notice.getNoticeType().name());
        data.put("recipientEmail", notice.getRecipientEmail());
        data.put("status", notice.getStatus().name());
        data.put("deliveryAttempts", notice.getDeliveryAttempts());
        data.put("lastAttemptAt", asText(notice.getLastAttemptAt()));
        data.put("deliveredAt", asText(notice.getDeliveredAt()));
        data.put("failureReason", notice.getFailureReason());
        return data;
    }

    private String asText(Instant value) {
        return value == null ? null : value.toString();
    }

    @FunctionalInterface
    private interface NoticeStateChange {

        void apply(PlatformChargeNotice notice);
    }
}
