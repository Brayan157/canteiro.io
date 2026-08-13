package com.renovar.canteiro.io.platform.subscription.domain;

import java.util.List;
import java.util.UUID;

public interface PlatformChargeNoticeRepository {

    boolean saveIfAbsent(PlatformChargeNotice notice);

    List<PlatformChargeNotice> findByChargeId(UUID chargeId);
}
