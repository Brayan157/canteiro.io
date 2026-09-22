package com.renovar.canteiro.io.customers.infrastructure.persistence;

import com.renovar.canteiro.io.customers.domain.FinalCustomerContact;
import com.renovar.canteiro.io.customers.domain.FinalCustomerContactRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaFinalCustomerContactRepository implements FinalCustomerContactRepository {

    private final FinalCustomerContactJpaRepository finalCustomerContactJpaRepository;
    private final FinalCustomerContactPersistenceMapper finalCustomerContactPersistenceMapper;

    @Override
    public FinalCustomerContact save(FinalCustomerContact contact) {
        if (contact.getId() != null) {
            FinalCustomerContactJpaEntity entity = finalCustomerContactJpaRepository.findById(contact.getId())
                    .orElseThrow(() -> new IllegalStateException("Final customer contact must exist before it can be updated"));
            finalCustomerContactPersistenceMapper.updateJpaEntity(entity, contact);
            return finalCustomerContactPersistenceMapper.toDomain(finalCustomerContactJpaRepository.save(entity));
        }
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

    @Override
    public Optional<FinalCustomerContact> findByIdAndFinalCustomerIdAndCompanyId(UUID id, UUID finalCustomerId, UUID companyId) {
        return finalCustomerContactJpaRepository.findByIdAndFinalCustomerIdAndCompanyId(id, finalCustomerId, companyId)
                .map(finalCustomerContactPersistenceMapper::toDomain);
    }
}
