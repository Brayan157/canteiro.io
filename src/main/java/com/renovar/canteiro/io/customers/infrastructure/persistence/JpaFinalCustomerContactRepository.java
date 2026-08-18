package com.renovar.canteiro.io.customers.infrastructure.persistence;

import com.renovar.canteiro.io.customers.domain.FinalCustomerContact;
import com.renovar.canteiro.io.customers.domain.FinalCustomerContactRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaFinalCustomerContactRepository implements FinalCustomerContactRepository {

    private final FinalCustomerContactJpaRepository finalCustomerContactJpaRepository;
    private final FinalCustomerContactPersistenceMapper finalCustomerContactPersistenceMapper;

    @Override
    public FinalCustomerContact save(FinalCustomerContact contact) {
        return finalCustomerContactPersistenceMapper.toDomain(
                finalCustomerContactJpaRepository.save(finalCustomerContactPersistenceMapper.toJpaEntity(contact))
        );
    }

    @Override
    public List<FinalCustomerContact> findByFinalCustomerIdAndCompanyId(UUID finalCustomerId, UUID companyId) {
        return finalCustomerContactJpaRepository.findByFinalCustomerIdAndCompanyId(finalCustomerId, companyId).stream()
                .map(finalCustomerContactPersistenceMapper::toDomain)
                .toList();
    }
}
