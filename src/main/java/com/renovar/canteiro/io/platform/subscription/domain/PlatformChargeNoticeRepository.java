package com.renovar.canteiro.io.platform.subscription.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlatformChargeNoticeRepository {

    boolean saveIfAbsent(PlatformChargeNotice notice);

    List<PlatformChargeNotice> findByChargeId(UUID chargeId);

    List<PlatformChargeNotice> claimPendingDeliveries(Instant attemptedAt, Instant staleBefore, int limit);

    Optional<PlatformChargeNotice> findByIdForUpdate(UUID noticeId);

    PlatformChargeNotice save(PlatformChargeNotice notice);
}
