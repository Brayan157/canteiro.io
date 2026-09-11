package com.renovar.canteiro.io.contracts.infrastructure.persistence;

import com.renovar.canteiro.io.contracts.domain.ContractDiscount;
import com.renovar.canteiro.io.contracts.domain.DiscountType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JpaContractDiscountRepositoryTest {

    @Mock private ContractDiscountJpaRepository jpaRepository;

    @Test
    void updatesTheExistingDiscountInsteadOfInsertingAnotherRow() {
        UUID companyId = UUID.randomUUID();
        UUID contractId = UUID.randomUUID();
        ContractDiscountJpaEntity existing = new ContractDiscountJpaEntity(
                companyId, contractId, DiscountType.FIXED, new BigDecimal("5.00"));
        when(jpaRepository.findByContractIdAndCompanyId(contractId, companyId)).thenReturn(Optional.of(existing));
        when(jpaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        JpaContractDiscountRepository repository = new JpaContractDiscountRepository(jpaRepository);

        ContractDiscount updated = repository.save(ContractDiscount.create(
                companyId, contractId, DiscountType.PERCENTAGE, new BigDecimal("10.0000")));

        assertEquals(DiscountType.PERCENTAGE, updated.getDiscountType());
        assertEquals(new BigDecimal("10.0000"), updated.getDiscountValue());
        verify(jpaRepository).save(existing);
    }
}
