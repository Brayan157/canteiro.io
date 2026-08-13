package com.renovar.canteiro.io.platform.subscription.domain;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Component
public class SubscriptionDunningPolicy {

    private static final long DELINQUENT_AFTER_DAYS = 5;
    private static final long BLOCKED_AFTER_DAYS = 10;

    public SubscriptionDunningAssessment assess(List<PlatformCharge> charges, LocalDate currentDate) {
        if (currentDate == null) {
            throw new IllegalArgumentException("Subscription dunning assessment date is required");
        }
        if (charges == null) {
            throw new IllegalArgumentException("Subscription dunning charges are required");
        }
        return charges.stream()
                .filter(charge -> charge.isOverdueOn(currentDate))
                .min(Comparator.comparing(PlatformCharge::getDueDate))
                .map(charge -> new SubscriptionDunningAssessment(accessLevelFor(charge, currentDate), charge.getId()))
                .orElseGet(SubscriptionDunningAssessment::fullAccess);
    }

    public Set<PlatformChargeNoticeType> noticesFor(PlatformCharge charge, LocalDate currentDate) {
        if (charge == null || currentDate == null || !charge.isUnpaidOn(currentDate)) {
            return Set.of();
        }
        long daysPastDue = daysPastDue(charge, currentDate);
        Set<PlatformChargeNoticeType> notices = EnumSet.of(PlatformChargeNoticeType.DUE_DATE);
        if (daysPastDue >= 1) {
            notices.add(PlatformChargeNoticeType.READ_ONLY);
        }
        if (daysPastDue >= DELINQUENT_AFTER_DAYS) {
            notices.add(PlatformChargeNoticeType.DELINQUENT);
        }
        if (daysPastDue >= BLOCKED_AFTER_DAYS) {
            notices.add(PlatformChargeNoticeType.BLOCKED);
        }
        return Set.copyOf(notices);
    }

    private SubscriptionAccessLevel accessLevelFor(PlatformCharge charge, LocalDate currentDate) {
        long daysPastDue = daysPastDue(charge, currentDate);
        if (daysPastDue >= BLOCKED_AFTER_DAYS) {
            return SubscriptionAccessLevel.BLOCKED;
        }
        if (daysPastDue >= DELINQUENT_AFTER_DAYS) {
            return SubscriptionAccessLevel.DELINQUENT_READ_ONLY;
        }
        return SubscriptionAccessLevel.READ_ONLY;
    }

    private long daysPastDue(PlatformCharge charge, LocalDate currentDate) {
        return ChronoUnit.DAYS.between(charge.getDueDate(), currentDate);
    }
}
