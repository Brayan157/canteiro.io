package com.renovar.canteiro.io.payables.domain;

import java.util.Optional;
import java.util.UUID;

public interface PayablePaymentInstructionRepository {
    void lockPayable(UUID companyId,UUID payableId);
    Optional<PayablePaymentInstruction> latest(UUID companyId,UUID payableId);
    PayablePaymentInstruction save(PayablePaymentInstruction revision);
}
