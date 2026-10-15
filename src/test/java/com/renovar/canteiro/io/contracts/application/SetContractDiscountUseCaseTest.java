package com.renovar.canteiro.io.contracts.application;

import com.renovar.canteiro.io.contracts.domain.Contract;
import com.renovar.canteiro.io.contracts.domain.ContractDiscountRepository;
import com.renovar.canteiro.io.contracts.domain.ContractRepository;
import com.renovar.canteiro.io.contracts.domain.ContractService;
import com.renovar.canteiro.io.contracts.domain.ContractServiceRepository;
import com.renovar.canteiro.io.contracts.domain.ContractServiceStatus;
import com.renovar.canteiro.io.contracts.domain.ContractStatus;
import com.renovar.canteiro.io.contracts.domain.DiscountType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class SetContractDiscountUseCaseTest {

    @Mock private ContractRepository contractRepository;
    @Mock private ContractDiscountRepository contractDiscountRepository;
    @Mock private ContractServiceRepository contractServiceRepository;
    @Mock private ApprovedContractBillingAmountProvider approvedContractBillingAmountProvider;

    @Test
    void blocksReducingDiscountBelowAnAlreadyAllocatedNegativeAdjustment() {
        Fixture fixture = fixture(new BigDecimal("80.00"));
        when(approvedContractBillingAmountProvider.approvedDiscountAmount(fixture.companyId(), fixture.contractId()))
                .thenReturn(new BigDecimal("20.00"));
        assertThrows(IllegalStateException.class, () -> fixture.service().set(
                fixture.companyId(), fixture.contractId(), DiscountType.FIXED, new BigDecimal("10.00")));
        verify(contractDiscountRepository, never()).save(any());
    }

    @Test
    void blocksDiscountThatWouldReduceTheContractBelowApprovedBilling() {
        Fixture fixture = fixture(new BigDecimal("90.00"));

        assertThrows(IllegalStateException.class, () -> fixture.service().set(
                fixture.companyId(), fixture.contractId(), DiscountType.FIXED, new BigDecimal("20.00")
        ));
        verify(contractDiscountRepository, never()).save(any());
    }

    @Test
    void savesDiscountWhenTheResultingNetAmountStillCoversApprovedBilling() {
        Fixture fixture = fixture(new BigDecimal("90.00"));
        when(contractDiscountRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = fixture.service().set(
                fixture.companyId(), fixture.contractId(), DiscountType.FIXED, new BigDecimal("10.00")
        );

        assertEquals(new BigDecimal("10.00"), result.getDiscountValue());
    }

    private Fixture fixture(BigDecimal approvedAmount) {
        UUID companyId = UUID.randomUUID();
        UUID contractId = UUID.randomUUID();
        Contract contract = Contract.rehydrate(contractId, companyId, UUID.randomUUID(), "C-01", "Contract",
                ContractStatus.ACTIVE, null, null, null, Instant.now(), Instant.now());
        ContractService service = ContractService.rehydrate(UUID.randomUUID(), companyId, contractId, null, "Service", null,
                ContractServiceStatus.ACTIVE, BigDecimal.ONE, new BigDecimal("100.00"), new BigDecimal("100.00"),
                null, null, BigDecimal.ZERO.setScale(2), new BigDecimal("100.00"), Instant.now(), Instant.now());
        when(contractRepository.findByIdAndCompanyIdWithBillingLock(contractId, companyId))
                .thenReturn(Optional.of(contract));
        when(contractServiceRepository.findByContractIdAndCompanyId(contractId, companyId)).thenReturn(List.of(service));
        when(approvedContractBillingAmountProvider.approvedNetAmount(companyId, contractId)).thenReturn(approvedAmount);
        lenient().when(approvedContractBillingAmountProvider.approvedDiscountAmount(companyId, contractId)).thenReturn(BigDecimal.ZERO);
        return new Fixture(companyId, contractId, new SetContractDiscountUseCase(
                contractRepository, contractDiscountRepository, contractServiceRepository,
                approvedContractBillingAmountProvider
        ));
    }

    private record Fixture(UUID companyId, UUID contractId, SetContractDiscountUseCase service) {
    }
}
