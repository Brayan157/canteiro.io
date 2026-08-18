package com.renovar.canteiro.io.customers.infrastructure.persistence;

import com.renovar.canteiro.io.customers.domain.FinalCustomerAddress;
import com.renovar.canteiro.io.customers.domain.FinalCustomerAddressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaFinalCustomerAddressRepository implements FinalCustomerAddressRepository {

    private final FinalCustomerAddressJpaRepository finalCustomerAddressJpaRepository;
    private final FinalCustomerAddressPersistenceMapper finalCustomerAddressPersistenceMapper;

    @Override
    public FinalCustomerAddress save(FinalCustomerAddress address) {
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
}
