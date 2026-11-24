package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.contracts.application.CalculateContractNetAmountUseCase;
import com.renovar.canteiro.io.contracts.domain.ContractRepository;
import com.renovar.canteiro.io.contracts.domain.ContractServiceRepository;
import com.renovar.canteiro.io.contracts.domain.ContractServiceStatus;
import com.renovar.canteiro.io.contracts.domain.ContractStatus;
import com.renovar.canteiro.io.measurements.domain.MeasurementContractCompositionRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementContractFinancialOrigin;
import com.renovar.canteiro.io.receivables.domain.BillingAllocation;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationRepository;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationStatus;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationType;
import com.renovar.canteiro.io.receivables.domain.NetBillingDistribution;
import com.renovar.canteiro.io.receivables.domain.NetBillingRepository;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NetBillingCapacityService {
    private final ContractRepository contracts;
    private final ContractServiceRepository services;
    private final CalculateContractNetAmountUseCase contractAmounts;
    private final BillingAllocationRepository allocations;
    private final MeasurementContractCompositionRepository compositions;
    private final NetBillingRepository operations;

    @Transactional(readOnly = true)
    public State calculate(UUID company, UUID contractId) {
        return calculateWithServices(company, contractId, services.findByContractIdAndCompanyId(contractId, company));
    }

    @Transactional(readOnly = true)
    public State calculateWithServices(UUID company, UUID contractId, List<com.renovar.canteiro.io.contracts.domain.ContractService> projected) {
        var contract = contracts.findByIdAndCompanyId(contractId, company).orElseThrow(() -> new TenantResourceNotFoundException("Contract"));
        var amount = contractAmounts.calculateWithServices(company, contractId, projected);
        List<BillingAllocation> active = allocations.findByContractIdAndCompanyId(contractId, company).stream()
                .filter(a -> a.getStatus() == BillingAllocationStatus.ACTIVE).toList();
        BigDecimal allocatedNet = active.stream().map(BillingAllocation::getAllocatedAmount).reduce(zero(), BigDecimal::add);
        BigDecimal grossUsed = active.stream().map(BillingAllocation::getCapacityAmount).reduce(zero(), BigDecimal::add);
        BigDecimal remainingNet = amount.netAmount().subtract(allocatedNet).setScale(2);
        BigDecimal remainingDiscount = amount.contractDiscountAmount().subtract(grossUsed.subtract(allocatedNet)).setScale(2);
        if (remainingDiscount.signum() < 0) throw new IllegalStateException("Committed discounts require financial reconciliation before net billing");
        var origins = compositions.findActiveContractFinancialOrigins(company, contractId);
        var legacy = operations.legacyConvertedServiceIds(company, contractId);
        Map<UUID, BigDecimal> baseUsed = new LinkedHashMap<>(), compositionUsed = new LinkedHashMap<>();
        for (var a : active) if (a.getType() == BillingAllocationType.SERVICE) {
            var target = a.getMeasurementContractCompositionId() == null ? baseUsed : compositionUsed;
            target.merge(a.getMeasurementContractCompositionId() == null ? a.getContractServiceId() : a.getMeasurementContractCompositionId(),
                    a.getCapacityAmount(), BigDecimal::add);
        }
        List<Position> grossPositions = new ArrayList<>();
        for (var service : projected) {
            if (service.getStatus() == ContractServiceStatus.CANCELLED) continue;
            boolean contractAllowed = contract.getStatus() != ContractStatus.CANCELLED;
            grossPositions.add(position(service.getId(), service.getName(), null,
                    service.getNetAmount().subtract(baseUsed.getOrDefault(service.getId(), zero())),
                    contractAllowed && (service.getId() == null || !legacy.contains(service.getId())), !contractAllowed ? "CONTRACT_CANCELLED" : "LEGACY_ORIGIN_REQUIRES_RECONCILIATION"));
            for (var origin : origins) if (origin.contractServiceId().equals(service.getId())) {
                grossPositions.add(position(service.getId(), service.getName(), origin,
                        origin.netAmount().subtract(compositionUsed.getOrDefault(origin.id(), zero())),
                        contractAllowed && origin.eligible(), !contractAllowed ? "CONTRACT_CANCELLED" : "RECONCILIATION_REQUIRED"));
            }
        }
        grossPositions.sort(Comparator.comparing(Position::key));
        Map<String, BigDecimal> weights = new LinkedHashMap<>();
        grossPositions.forEach(p -> weights.put(p.key(), p.grossAvailableAmount()));
        BigDecimal totalGross = weights.values().stream().reduce(zero(), BigDecimal::add);
        if (totalGross.compareTo(amount.serviceSubtotal().subtract(grossUsed)) != 0
                || totalGross.subtract(remainingDiscount).compareTo(remainingNet) != 0) {
            throw new IllegalStateException("Contract origins require financial reconciliation before net billing");
        }
        var netShares = NetBillingDistribution.allocate(remainingNet, weights);
        var positions = grossPositions.stream().map(p -> new Position(p.key(), p.contractServiceId(), p.serviceName(), p.origin(),
                p.grossAvailableAmount(), netShares.get(p.key()), p.grossAvailableAmount().subtract(netShares.get(p.key())), p.eligible(), p.reason())).toList();
        StringBuilder fingerprint = new StringBuilder(contractId + "/" + contract.getCurrency() + "/" + amount.netAmount() + "/" + remainingDiscount);
        positions.forEach(p -> fingerprint.append('|').append(p.key()).append(':').append(p.grossAvailableAmount()).append(':').append(p.netAvailableAmount()).append(':').append(p.eligible())
                .append(':').append(p.origin() == null ? "BASE" : p.origin().acceptedOn()));
        active.stream().sorted(Comparator.comparing(BillingAllocation::getId)).forEach(a -> fingerprint.append('|').append(a.getId())
                .append(':').append(a.getAllocatedAmount()).append(':').append(a.getCapacityAmount()));
        return new State(contractId, contract.getCurrency(), hash(fingerprint.toString()), amount.netAmount(), allocatedNet,
                remainingNet, remainingDiscount, positions);
    }

    private Position position(UUID service, String name, MeasurementContractFinancialOrigin origin, BigDecimal gross, boolean eligible, String reason) {
        if (gross.signum() < 0) throw new IllegalStateException("An origin is committed above its official gross capacity");
        return new Position(key(service, origin == null ? null : origin.id()), service, name, origin, gross.setScale(2), zero(), zero(), eligible, eligible ? "AVAILABLE" : reason);
    }
    public static String key(UUID service, UUID origin) { return origin == null ? "BASE/" + service : "COMPOSITION/" + service + "/" + origin; }
    public static String hash(String value) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 is unavailable", e); }
    }
    private static BigDecimal zero() { return BigDecimal.ZERO.setScale(2); }
    public record Position(String key, UUID contractServiceId, String serviceName, MeasurementContractFinancialOrigin origin,
            BigDecimal grossAvailableAmount, BigDecimal netAvailableAmount, BigDecimal uncommittedDiscountAmount, boolean eligible, String reason) {
    }
    public record State(UUID contractId, CurrencyCode currency, String capacityVersion, BigDecimal contractNetAmount,
            BigDecimal activeAllocatedAmount, BigDecimal remainingNetAmount, BigDecimal remainingDiscountAmount, List<Position> positions) {
        public BigDecimal availableForService(UUID service) {
            return positions.stream().filter(p -> java.util.Objects.equals(p.contractServiceId(), service) && p.eligible()).map(Position::netAvailableAmount).reduce(zero(), BigDecimal::add);
        }
        public BigDecimal availableForOrigin(UUID origin) {
            return positions.stream().filter(p -> p.origin() != null && p.origin().id().equals(origin) && p.eligible()).map(Position::netAvailableAmount).reduce(zero(), BigDecimal::add);
        }
    }
}
