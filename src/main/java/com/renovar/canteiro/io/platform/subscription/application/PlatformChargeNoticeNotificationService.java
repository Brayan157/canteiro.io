package com.renovar.canteiro.io.platform.subscription.application;

import com.renovar.canteiro.io.notifications.application.NotificationPort;
import com.renovar.canteiro.io.notifications.domain.EmailNotification;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformCharge;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeNotice;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeNoticeType;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PlatformChargeNoticeNotificationService {

    private final PlatformChargeNoticeDeliveryLifecycleService deliveryLifecycleService;
    private final PlatformChargeRepository platformChargeRepository;
    private final NotificationPort notificationPort;
    private final Clock clock;

    public NotificationDeliveryRunResult deliverPendingNotices() {
        List<PlatformChargeNotice> notices = deliveryLifecycleService.claimPendingDeliveries();
        int delivered = 0;
        int failed = 0;
        int cancelled = 0;
        for (PlatformChargeNotice notice : notices) {
            try {
                PlatformCharge charge = platformChargeRepository.findById(notice.getChargeId())
                        .orElseThrow(() -> new IllegalStateException("Platform charge was not found"));
                if (!charge.isUnpaidOn(LocalDate.now(clock))) {
                    deliveryLifecycleService.cancel(notice.getId());
                    cancelled++;
                    continue;
                }
                notificationPort.send(toEmailNotification(notice, charge));
                deliveryLifecycleService.markDelivered(notice.getId());
                delivered++;
            } catch (RuntimeException exception) {
                deliveryLifecycleService.markFailed(notice.getId(), failureReason(exception));
                failed++;
            }
        }
        return new NotificationDeliveryRunResult(notices.size(), delivered, failed, cancelled);
    }

    private EmailNotification toEmailNotification(PlatformChargeNotice notice, PlatformCharge charge) {
        return new EmailNotification(
                notice.getRecipientEmail(),
                subjectFor(notice.getNoticeType()),
                "A cobranca da sua assinatura no valor de R$ " + charge.getAmount().toPlainString()
                        + " venceu em " + charge.getDueDate() + ".\n\n"
                        + messageFor(notice.getNoticeType()) + "\n\n"
                        + "Regularize a cobranca para manter o acesso completo ao Canteiro.io."
        );
    }

    private String subjectFor(PlatformChargeNoticeType noticeType) {
        return switch (noticeType) {
            case DUE_DATE -> "Canteiro.io - cobranca vence hoje";
            case READ_ONLY -> "Canteiro.io - acesso em modo consulta";
            case DELINQUENT -> "Canteiro.io - assinatura inadimplente";
            case BLOCKED -> "Canteiro.io - acesso bloqueado";
        };
    }

    private String messageFor(PlatformChargeNoticeType noticeType) {
        return switch (noticeType) {
            case DUE_DATE -> "O vencimento e hoje. Evite restricoes no acesso realizando o pagamento.";
            case READ_ONLY -> "Seu acesso foi alterado para consulta ate a regularizacao do pagamento.";
            case DELINQUENT -> "Sua assinatura permanece inadimplente e o acesso continua somente para consulta.";
            case BLOCKED -> "O acesso da empresa foi bloqueado ate a regularizacao do pagamento.";
        };
    }

    private String failureReason(RuntimeException exception) {
        return exception.getClass().getSimpleName();
    }
}
