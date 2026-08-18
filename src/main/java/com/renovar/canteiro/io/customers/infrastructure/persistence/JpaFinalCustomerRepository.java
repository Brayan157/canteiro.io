package com.renovar.canteiro.io.customers.infrastructure.persistence;

import com.renovar.canteiro.io.customers.domain.FinalCustomer;
import com.renovar.canteiro.io.customers.domain.FinalCustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaFinalCustomerRepository implements FinalCustomerRepository {

    private final FinalCustomerJpaRepository finalCustomerJpaRepository;
    private final FinalCustomerPersistenceMapper finalCustomerPersistenceMapper;

    @Override
    public FinalCustomer save(FinalCustomer finalCustomer) {
        if (finalCustomer.getId() == null) {
            return finalCustomerPersistenceMapper.toDomain(
                    finalCustomerJpaRepository.save(finalCustomerPersistenceMapper.toJpaEntity(finalCustomer))
            );
        }
        FinalCustomerJpaEntity entity = finalCustomerJpaRepository.findById(finalCustomer.getId())
                .orElseThrow(() -> new IllegalStateException("Final customer must exist before it can be updated"));
        finalCustomerPersistenceMapper.updateJpaEntity(entity, finalCustomer);
        return finalCustomerPersistenceMapper.toDomain(finalCustomerJpaRepository.save(entity));
    }

    @Override
    public Optional<FinalCustomer> findByIdAndCompanyId(UUID id, UUID companyId) {
        return finalCustomerJpaRepository.findByIdAndCompanyId(id, companyId)
                .map(finalCustomerPersistenceMapper::toDomain);
    }

    @Override
    public Optional<FinalCustomer> findByCompanyIdAndDocument(UUID companyId, String document) {
        return finalCustomerJpaRepository.findByCompanyIdAndDocument(companyId, document.replaceAll("\\D", ""))
                .map(finalCustomerPersistenceMapper::toDomain);
    }

    @Override
    public Page<FinalCustomer> findByCompanyId(UUID companyId, Pageable pageable) {
        return finalCustomerJpaRepository.findByCompanyId(companyId, pageable)
                .map(finalCustomerPersistenceMapper::toDomain);
    }
}
