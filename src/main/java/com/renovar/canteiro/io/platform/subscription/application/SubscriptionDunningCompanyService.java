package com.renovar.canteiro.io.platform.subscription.application;

import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.platform.company.domain.Company;
import com.renovar.canteiro.io.platform.company.domain.CompanyRepository;
import com.renovar.canteiro.io.platform.subscription.domain.CompanySubscriptionAccess;
import com.renovar.canteiro.io.platform.subscription.domain.CompanySubscriptionAccessRepository;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformCharge;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeNotice;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeNoticeRepository;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeNoticeType;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeRepository;
import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionAccessLevel;
import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionDunningAssessment;
import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionDunningPolicy;
import com.renovar.canteiro.io.platform.subscription.domain.TrustUnlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class SubscriptionDunningCompanyService {

    private static final String ORIGIN = "subscription-dunning";

    private final PlatformChargeRepository platformChargeRepository;
    private final CompanySubscriptionAccessRepository companySubscriptionAccessRepository;
    private final PlatformChargeNoticeRepository platformChargeNoticeRepository;
    private final CompanyRepository companyRepository;
    private final SubscriptionDunningPolicy subscriptionDunningPolicy;
    private final TrustUnlockRepository trustUnlockRepository;
    private final AuditEventRecorder auditEventRecorder;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SubscriptionDunningRunResult evaluateCompany(UUID companyId) {
        return evaluateCompanyInCurrentTransaction(companyId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public SubscriptionDunningRunResult reevaluateCompany(UUID companyId) {
        return evaluateCompanyInCurrentTransaction(companyId);
    }

    private SubscriptionDunningRunResult evaluateCompanyInCurrentTransaction(UUID companyId) {
        if (companyId == null) {
            throw new IllegalArgumentException("Subscription dunning requires a company");
        }
        companySubscriptionAccessRepository.lockCompanyId(companyId);
        LocalDate currentDate = LocalDate.now(clock);
        List<PlatformCharge> charges = platformChargeRepository.findOutstandingByCompanyIdForDunning(companyId);
        Set<UUID> trustedChargeIds = trustUnlockRepository.findActiveChargeIdsByCompanyId(companyId, clock.instant());
        SubscriptionDunningAssessment assessment = subscriptionDunningPolicy.assess(charges, currentDate, trustedChargeIds);
        int accessChanges = updateAccess(companyId, assessment, currentDate) ? 1 : 0;
        int noticesCreated = createNotices(companyId, charges, currentDate);
        return new SubscriptionDunningRunResult(1, accessChanges, noticesCreated);
    }

    private boolean updateAccess(
            UUID companyId,
            SubscriptionDunningAssessment assessment,
            LocalDate currentDate
    ) {
        var existingAccess = companySubscriptionAccessRepository.findByCompanyId(companyId);
        if (existingAccess.isEmpty() && assessment.accessLevel() == SubscriptionAccessLevel.FULL) {
            return false;
        }

        CompanySubscriptionAccess access = existingAccess.orElseGet(() -> CompanySubscriptionAccess.create(
                companyId,
                assessment.accessLevel(),
                assessment.restrictionChargeId(),
                currentDate
        ));
        Map<String, Object> beforeData = existingAccess.map(this::accessAuditData).orElse(null);
        boolean changed = existingAccess.isEmpty() || access.update(
                assessment.accessLevel(), assessment.restrictionChargeId(), currentDate
        );
        if (!changed) {
            return false;
        }

        CompanySubscriptionAccess savedAccess = companySubscriptionAccessRepository.save(access);
        auditEventRecorder.recordSystemAction(
                companyId,
                AuditModule.PLATFORM,
                beforeData == null ? AuditAction.CREATE : AuditAction.UPDATE,
                "CompanySubscriptionAccess",
                companyId,
                beforeData,
                accessAuditData(savedAccess),
                Map.of(ORIGIN, true)
        );
        return true;
    }

    private int createNotices(UUID companyId, List<PlatformCharge> charges, LocalDate currentDate) {
        List<PlatformCharge> chargesWithNotices = charges.stream()
                .filter(charge -> !subscriptionDunningPolicy.noticesFor(charge, currentDate).isEmpty())
                .toList();
        if (chargesWithNotices.isEmpty()) {
            return 0;
        }

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalStateException("Dunning company does not exist"));
        int noticesCreated = 0;
        for (PlatformCharge charge : chargesWithNotices) {
            for (PlatformChargeNoticeType noticeType : subscriptionDunningPolicy.noticesFor(charge, currentDate)) {
                PlatformChargeNotice notice = PlatformChargeNotice.create(
                        companyId, charge.getId(), noticeType, company.getEmail(), currentDate
                );
                if (platformChargeNoticeRepository.saveIfAbsent(notice)) {
                    auditEventRecorder.recordSystemAction(
                            companyId,
                            AuditModule.PLATFORM,
                            AuditAction.CREATE,
                            "PlatformChargeNotice",
                            notice.getId(),
                            null,
                            noticeAuditData(notice),
                            Map.of(ORIGIN, true)
                    );
                    noticesCreated++;
                }
            }
        }
        return noticesCreated;
    }

    private Map<String, Object> accessAuditData(CompanySubscriptionAccess access) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("accessLevel", access.getAccessLevel().name());
        data.put("restrictionChargeId", access.getRestrictionChargeId());
        data.put("effectiveOn", access.getEffectiveOn().toString());
        return data;
    }

    private Map<String, Object> noticeAuditData(PlatformChargeNotice notice) {
        return Map.of(
                "chargeId", notice.getChargeId(),
                "noticeType", notice.getNoticeType().name(),
                "occurredOn", notice.getOccurredOn().toString(),
                "status", notice.getStatus().name()
        );
    }
}
