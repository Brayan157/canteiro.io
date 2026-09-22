package com.renovar.canteiro.io.customers.infrastructure.persistence;

import com.renovar.canteiro.io.customers.domain.FinalCustomerAddress;
import com.renovar.canteiro.io.customers.domain.FinalCustomerAddressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaFinalCustomerAddressRepository implements FinalCustomerAddressRepository {

    private final FinalCustomerAddressJpaRepository finalCustomerAddressJpaRepository;
    private final FinalCustomerAddressPersistenceMapper finalCustomerAddressPersistenceMapper;

    @Override
    public FinalCustomerAddress save(FinalCustomerAddress address) {
        if (address.getId() != null) {
            FinalCustomerAddressJpaEntity entity = finalCustomerAddressJpaRepository.findById(address.getId())
                    .orElseThrow(() -> new IllegalStateException("Final customer address must exist before it can be updated"));
            finalCustomerAddressPersistenceMapper.updateJpaEntity(entity, address);
            return finalCustomerAddressPersistenceMapper.toDomain(finalCustomerAddressJpaRepository.save(entity));
        }
        return finalCustomerAddressPersistenceMapper.toDomain(
                finalCustomerAddressJpaRepository.save(finalCustomerAddressPersistenceMapper.toJpaEntity(address))
        );
    }

    @Override
    public List<FinalCustomerAddress> findByFinalCustomerIdAndCompanyId(UUID finalCustomerId, UUID companyId) {
        return finalCustomerAddressJpaRepository.findByFinalCustomerIdAndCompanyId(finalCustomerId, companyId).stream()
                .map(finalCustomerAddressPersistenceMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<FinalCustomerAddress> findByIdAndFinalCustomerIdAndCompanyId(UUID id, UUID finalCustomerId, UUID companyId) {
        return finalCustomerAddressJpaRepository.findByIdAndFinalCustomerIdAndCompanyId(id, finalCustomerId, companyId)
                .map(finalCustomerAddressPersistenceMapper::toDomain);
    }
}
