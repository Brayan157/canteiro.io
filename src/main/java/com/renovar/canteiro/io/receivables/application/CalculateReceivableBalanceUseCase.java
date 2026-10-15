package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.receivables.domain.Receivable;
import com.renovar.canteiro.io.receivables.domain.ReceivableBalance;
import com.renovar.canteiro.io.receivables.domain.ReceivableRepository;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementRepository;
import com.renovar.canteiro.io.receivables.domain.TaxObligationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CalculateReceivableBalanceUseCase {

    private final ReceivableRepository receivableRepository;
    private final ReceivableSettlementRepository receivableSettlementRepository;
    private final TaxObligationRepository taxObligationRepository;
    private final com.renovar.canteiro.io.receivables.domain.ReceivableOriginRepository originRepository;

    public ReceivableBalance calculate(UUID companyId, UUID receivableId) {
        Receivable receivable = receivableRepository.findByIdAndCompanyId(receivableId, companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Receivable"));
        return new ReceivableBalance(
                receivableId,
                receivable.getOriginalAmount(),
                taxObligationRepository.registeredWithheldAmountByReceivableIdAndCompanyId(receivableId, companyId),
                receivableSettlementRepository.confirmedAmountByReceivableIdAndCompanyId(receivableId, companyId),
                originRepository.effectiveCredits(companyId,receivableId)
        );
    }
}
