package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.contracts.application.CalculateContractNetAmountUseCase;
import com.renovar.canteiro.io.contracts.domain.Contract;
import com.renovar.canteiro.io.contracts.domain.ContractNetAmount;
import com.renovar.canteiro.io.contracts.domain.ContractRepository;
import com.renovar.canteiro.io.contracts.domain.ContractService;
import com.renovar.canteiro.io.contracts.domain.ContractServiceRepository;
import com.renovar.canteiro.io.contracts.domain.ContractServiceStatus;
import com.renovar.canteiro.io.contracts.domain.ContractStatus;
import com.renovar.canteiro.io.receivables.domain.BillingAllocation;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationRepository;
import com.renovar.canteiro.io.receivables.domain.ContractBillingBalance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValidateContractBillingLimitUseCaseTest {

    @Mock private ContractRepository contractRepository;
    @Mock private ContractServiceRepository contractServiceRepository;
    @Mock private BillingAllocationRepository billingAllocationRepository;
    @Mock private CalculateContractNetAmountUseCase calculateContractNetAmountUseCase;
    @Mock private CalculateContractBillingBalanceUseCase calculateContractBillingBalanceUseCase;

    private ValidateContractBillingLimitUseCase serviceUnderTest;
    private UUID companyId;
    private UUID contractId;
    private UUID serviceId;

    @BeforeEach
    void setUp() {
        companyId = UUID.randomUUID();
        contractId = UUID.randomUUID();
        serviceId = UUID.randomUUID();
        Contract contract = Contract.rehydrate(contractId, companyId, UUID.randomUUID(), "C-01", "Contract",
                ContractStatus.ACTIVE, null, null, null, Instant.now(), Instant.now());
        when(contractRepository.findByIdAndCompanyIdWithBillingLock(contractId, companyId))
                .thenReturn(Optional.of(contract));
        when(billingAllocationRepository.findByContractIdAndCompanyId(contractId, companyId)).thenReturn(List.of());
        when(contractServiceRepository.findByContractIdAndCompanyId(contractId, companyId))
                .thenReturn(List.of(contractService("100.00")));
        serviceUnderTest = new ValidateContractBillingLimitUseCase(
                contractRepository, contractServiceRepository, billingAllocationRepository,
                calculateContractNetAmountUseCase, calculateContractBillingBalanceUseCase
        );
    }

    @Test
    void rejectsAllocationsThatExceedTheReferencedServiceNetAmount() {
        when(calculateContractBillingBalanceUseCase.calculate(companyId, contractId))
                .thenReturn(new ContractBillingBalance(new BigDecimal("200.00"), BigDecimal.ZERO.setScale(2),
                        new BigDecimal("200.00")));

        assertThrows(BillingLimitExceededException.class, () -> serviceUnderTest.requireAvailableForActivation(
                companyId, contractId, List.of(serviceAllocation("101.00"))
        ));
    }

    @Test
    void rejectsNegativeAllocationsWithoutContractDiscountBacking() {
        when(calculateContractBillingBalanceUseCase.calculate(companyId, contractId))
                .thenReturn(new ContractBillingBalance(new BigDecimal("100.00"), BigDecimal.ZERO.setScale(2),
                        new BigDecimal("100.00")));
        when(calculateContractNetAmountUseCase.calculate(companyId, contractId))
                .thenReturn(new ContractNetAmount(new BigDecimal("100.00"), BigDecimal.ZERO.setScale(2),
                        new BigDecimal("100.00")));

        assertThrows(BillingLimitExceededException.class, () -> serviceUnderTest.requireAvailableForActivation(
                companyId, contractId, List.of(serviceAllocation("100.00"), discountAllocation("-10.00"))
        ));
    }

    @Test
    void acceptsServiceAndNegativeAdjustmentWithinTheirCanonicalLimits() {
        when(calculateContractBillingBalanceUseCase.calculate(companyId, contractId))
                .thenReturn(new ContractBillingBalance(new BigDecimal("90.00"), BigDecimal.ZERO.setScale(2),
                        new BigDecimal("90.00")));
        when(calculateContractNetAmountUseCase.calculate(companyId, contractId))
                .thenReturn(new ContractNetAmount(new BigDecimal("100.00"), new BigDecimal("10.00"),
                        new BigDecimal("90.00")));

        assertDoesNotThrow(() -> serviceUnderTest.requireAvailableForActivation(
                companyId, contractId, List.of(serviceAllocation("100.00"), discountAllocation("-10.00"))
        ));
    }

    private ContractService contractService(String netAmount) {
        return ContractService.rehydrate(serviceId, companyId, contractId, null, "Service", null,
                ContractServiceStatus.ACTIVE, BigDecimal.ONE, new BigDecimal(netAmount), new BigDecimal(netAmount),
                null, null, BigDecimal.ZERO.setScale(2), new BigDecimal(netAmount), Instant.now(), Instant.now());
    }

    private BillingAllocation serviceAllocation(String amount) {
        return BillingAllocation.createService(companyId, UUID.randomUUID(), contractId, serviceId,
                new BigDecimal(amount)).activate();
    }

    private BillingAllocation discountAllocation(String amount) {
        return BillingAllocation.createContractDiscount(companyId, UUID.randomUUID(), contractId,
                new BigDecimal(amount)).activate();
    }
}
