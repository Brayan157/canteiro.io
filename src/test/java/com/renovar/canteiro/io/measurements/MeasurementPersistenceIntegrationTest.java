package com.renovar.canteiro.io.measurements;

import com.renovar.canteiro.io.AbstractPostgresIntegrationTest;
import com.renovar.canteiro.io.contracts.domain.Contract;
import com.renovar.canteiro.io.contracts.domain.ContractRepository;
import com.renovar.canteiro.io.contracts.domain.ContractService;
import com.renovar.canteiro.io.contracts.domain.ContractStatus;
import com.renovar.canteiro.io.contracts.domain.ContractServiceRepository;
import com.renovar.canteiro.io.contracts.domain.ContractServiceStatus;
import com.renovar.canteiro.io.customers.domain.FinalCustomer;
import com.renovar.canteiro.io.customers.domain.FinalCustomerRepository;
import com.renovar.canteiro.io.customers.domain.FinalCustomerType;
import com.renovar.canteiro.io.measurements.domain.Measurement;
import com.renovar.canteiro.io.measurements.domain.MeasurementDiscount;
import com.renovar.canteiro.io.measurements.domain.MeasurementDiscountRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementDiscountType;
import com.renovar.canteiro.io.measurements.domain.MeasurementRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementItem;
import com.renovar.canteiro.io.measurements.domain.MeasurementItemContractServiceConversion;
import com.renovar.canteiro.io.measurements.domain.MeasurementItemContractServiceConversionRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementItemRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersion;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementStatus;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionStatus;
import com.renovar.canteiro.io.platform.company.domain.Company;
import com.renovar.canteiro.io.platform.company.domain.CompanyRepository;
import com.renovar.canteiro.io.works.domain.Work;
import com.renovar.canteiro.io.works.domain.WorkExecutionLocationType;
import com.renovar.canteiro.io.works.domain.WorkRepository;
import com.renovar.canteiro.io.works.domain.WorkStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MeasurementPersistenceIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private FinalCustomerRepository finalCustomerRepository;
    @Autowired private WorkRepository workRepository;
    @Autowired private ContractRepository contractRepository;
    @Autowired private ContractServiceRepository contractServiceRepository;
    @Autowired private MeasurementRepository measurementRepository;
    @Autowired private MeasurementVersionRepository measurementVersionRepository;
    @Autowired private MeasurementItemRepository measurementItemRepository;
    @Autowired private MeasurementDiscountRepository measurementDiscountRepository;
    @Autowired private MeasurementItemContractServiceConversionRepository conversionRepository;

    @Test
    void persistsMeasurementWithOptionalContractFromTheSameWork() {
        Company company = createCompany();
        Work work = createWork(company);
        Contract contract = contractRepository.save(Contract.create(
                company.getId(), work.getId(), "C-1", "Contract", ContractStatus.OPEN, null, null, null
        ));

        Measurement measurement = measurementRepository.save(Measurement.create(
                company.getId(), work.getId(), contract.getId(), "M-1", "Initial measurement", LocalDate.now()
        ));

        assertEquals(contract.getId(), measurementRepository.findByIdAndCompanyId(measurement.getId(), company.getId())
                .orElseThrow().getContractId());
    }

    @Test
    void rejectsContractFromAnotherWork() {
        Company company = createCompany();
        Work measurementWork = createWork(company);
        Work contractWork = createWork(company);
        Contract contract = contractRepository.save(Contract.create(
                company.getId(), contractWork.getId(), "C-2", "Other contract", ContractStatus.OPEN, null, null, null
        ));

        assertThrows(DataIntegrityViolationException.class, () -> measurementRepository.save(Measurement.create(
                company.getId(), measurementWork.getId(), contract.getId(), null, null, null
        )));
    }

    @Test
    void rejectsMeasurementWorkFromAnotherTenant() {
        Company firstCompany = createCompany();
        Company secondCompany = createCompany();
        Work firstCompanyWork = createWork(firstCompany);

        assertThrows(DataIntegrityViolationException.class, () -> measurementRepository.save(Measurement.create(
                secondCompany.getId(), firstCompanyWork.getId(), null, null, null, null
        )));
    }

    @Test
    void persistsVersionedItemsWithinTheMeasurementTenant() {
        Company company = createCompany();
        Work work = createWork(company);
        Measurement measurement = measurementRepository.save(Measurement.create(
                company.getId(), work.getId(), null, "M-2", null, LocalDate.now()
        ));
        MeasurementVersion version = measurementVersionRepository.save(MeasurementVersion.create(
                company.getId(), measurement.getId(), 1
        ));
        MeasurementItem item = measurementItemRepository.save(MeasurementItem.createSquareMeter(
                company.getId(), version.getId(), 1, "Assembly", "Square meter item",
                new BigDecimal("12.5000"), new BigDecimal("10.00")
        ));
        MeasurementItem linearItem = measurementItemRepository.save(MeasurementItem.createLinearMeter(
                company.getId(), version.getId(), 2, "Assembly", "Linear meter item",
                new BigDecimal("15.0000"), new BigDecimal("10.00")
        ));
        MeasurementItem kilogramItem = measurementItemRepository.save(MeasurementItem.createKilogramPerSquareMeter(
                company.getId(), version.getId(), 3, "Fabrication", "Kilogram per square meter item",
                new BigDecimal("2.5000"), new BigDecimal("10.0000"), new BigDecimal("10.00")
        ));
        MeasurementItem linearKilogramItem = measurementItemRepository.save(MeasurementItem.createKilogramPerLinearMeter(
                company.getId(), version.getId(), 4, "Assembly", "Kilogram per linear meter item",
                new BigDecimal("2.0000"), new BigDecimal("10.0000"), new BigDecimal("10.00")
        ));

        MeasurementItem persistedItem = measurementItemRepository.findByIdAndCompanyId(item.getId(), company.getId())
                .orElseThrow();
        assertEquals(version.getId(), persistedItem.getMeasurementVersionId());
        assertEquals(new BigDecimal("125.00"), persistedItem.getTotalAmount());
        assertEquals(new BigDecimal("150.00"), measurementItemRepository
                .findByIdAndCompanyId(linearItem.getId(), company.getId()).orElseThrow().getTotalAmount());
        assertEquals(new BigDecimal("25.0000"), measurementItemRepository
                .findByIdAndCompanyId(kilogramItem.getId(), company.getId()).orElseThrow().getTotalWeightKg());
        assertEquals(new BigDecimal("200.00"), measurementItemRepository
                .findByIdAndCompanyId(linearKilogramItem.getId(), company.getId()).orElseThrow().getTotalAmount());

        measurementDiscountRepository.save(MeasurementDiscount.create(
                company.getId(), version.getId(), MeasurementDiscountType.FIXED, new BigDecimal("10.00")
        ));
        assertEquals(new BigDecimal("10.00"), measurementDiscountRepository
                .findByMeasurementVersionIdAndCompanyId(version.getId(), company.getId()).orElseThrow().getDiscountValue());
    }

    @Test
    void persistsTheExternalAcceptanceWorkflowWithoutRecreatingTheMeasurementOrVersion() {
        Company company = createCompany();
        Work work = createWork(company);
        Measurement measurement = measurementRepository.save(Measurement.create(
                company.getId(), work.getId(), null, "M-3", null, LocalDate.now()
        ));
        MeasurementVersion version = measurementVersionRepository.save(MeasurementVersion.create(
                company.getId(), measurement.getId(), 1
        ));
        measurementItemRepository.save(MeasurementItem.createSquareMeter(
                company.getId(), version.getId(), 1, "Assembly", "Accepted item",
                new BigDecimal("10.0000"), new BigDecimal("10.00")
        ));

        measurement.markSent();
        version.markSent();
        measurement = measurementRepository.save(measurement);
        version = measurementVersionRepository.save(version);
        measurement.markPendingAcceptance();
        version.markPendingAcceptance();
        measurement = measurementRepository.save(measurement);
        version = measurementVersionRepository.save(version);
        measurement.recordExternalAcceptance(true);
        version.recordExternalAcceptance(true, LocalDate.of(2026, 8, 18), "Customer email");
        measurement = measurementRepository.save(measurement);
        version = measurementVersionRepository.save(version);

        assertEquals(MeasurementStatus.ACCEPTED, measurementRepository
                .findByIdAndCompanyId(measurement.getId(), company.getId()).orElseThrow().getStatus());
        MeasurementVersion persistedVersion = measurementVersionRepository
                .findByIdAndCompanyId(version.getId(), company.getId()).orElseThrow();
        assertEquals(MeasurementVersionStatus.ACCEPTED, persistedVersion.getStatus());
        assertEquals(LocalDate.of(2026, 8, 18), persistedVersion.getExternalAcceptanceOn());
        UUID acceptedVersionId = persistedVersion.getId();

        assertThrows(JpaSystemException.class, () -> measurementItemRepository.save(
                MeasurementItem.createSquareMeter(company.getId(), acceptedVersionId, 2, "Assembly", "Late item",
                        new BigDecimal("1.0000"), new BigDecimal("10.00"))
        ));
        assertThrows(JpaSystemException.class, () -> measurementDiscountRepository.save(
                MeasurementDiscount.create(company.getId(), acceptedVersionId, MeasurementDiscountType.FIXED,
                        new BigDecimal("10.00"))
        ));

        measurement.startRevision();
        Measurement persistedMeasurement = measurementRepository.save(measurement);
        MeasurementVersion revision = measurementVersionRepository.save(MeasurementVersion.createRevision(
                company.getId(), persistedMeasurement.getId(), 2, persistedVersion.getId()
        ));

        assertEquals(MeasurementStatus.DRAFT, persistedMeasurement.getStatus());
        assertEquals(persistedVersion.getId(), revision.getPreviousVersionId());
        assertEquals(MeasurementVersionStatus.DRAFT, revision.getStatus());
    }

    @Test
    void rejectsStaleMeasurementLifecycleWritesWithOptimisticLocking() {
        Company company = createCompany();
        Measurement created = measurementRepository.save(Measurement.create(
                company.getId(), createWork(company).getId(), null, "M-lock", null, LocalDate.now()
        ));
        Measurement stale = measurementRepository.findByIdAndCompanyId(created.getId(), company.getId()).orElseThrow();
        Measurement current = measurementRepository.findByIdAndCompanyId(created.getId(), company.getId()).orElseThrow();

        current.markSent();
        measurementRepository.save(current);
        stale.markSent();

        assertThrows(org.springframework.orm.ObjectOptimisticLockingFailureException.class,
                () -> measurementRepository.save(stale));
    }

    @Test
    void persistsOnlyOneItemConversionAndDoesNotExposeItToAnotherTenant() {
        Company company = createCompany();
        Work work = createWork(company);
        Contract contract = contractRepository.save(Contract.create(
                company.getId(), work.getId(), "C-conversion", "Contract", ContractStatus.OPEN, null, null, null
        ));
        Measurement measurement = measurementRepository.save(Measurement.create(
                company.getId(), work.getId(), contract.getId(), "M-conversion", null, LocalDate.now()
        ));
        MeasurementVersion version = measurementVersionRepository.save(MeasurementVersion.create(
                company.getId(), measurement.getId(), 1
        ));
        MeasurementItem item = measurementItemRepository.save(MeasurementItem.createSquareMeter(
                company.getId(), version.getId(), 1, "Roofing", "Galvanized tile installation",
                new BigDecimal("10.0000"), new BigDecimal("25.00")
        ));
        ContractService service = contractServiceRepository.save(ContractService.create(
                company.getId(), contract.getId(), item.getActivity(), item.getDescription(),
                ContractServiceStatus.ACTIVE, item.getAreaSquareMeters(), item.getUnitPrice()
        ));

        MeasurementItemContractServiceConversion conversion = conversionRepository.save(
                MeasurementItemContractServiceConversion.create(company.getId(), version.getId(), item.getId(),
                        contract.getId(), service.getId())
        );

        assertEquals(conversion.getId(), conversionRepository.findByMeasurementItemIdAndCompanyId(item.getId(), company.getId())
                .orElseThrow().getId());
        assertThrows(DataIntegrityViolationException.class, () -> conversionRepository.save(
                MeasurementItemContractServiceConversion.create(company.getId(), version.getId(), item.getId(),
                        contract.getId(), service.getId())
        ));
        assertEquals(0, conversionRepository.findByMeasurementVersionIdAndCompanyId(version.getId(), UUID.randomUUID()).size());
    }

    private Company createCompany() {
        String value = UUID.randomUUID().toString();
        return companyRepository.save(Company.create(
                "Company " + value, "measurement", Long.toUnsignedString(UUID.randomUUID().getLeastSignificantBits()),
                value + "@example.com", null, null, null
        ));
    }

    private Work createWork(Company company) {
        FinalCustomer customer = finalCustomerRepository.save(FinalCustomer.create(
                company.getId(), FinalCustomerType.LEGAL, "Customer " + UUID.randomUUID(), nextLegalDocument()
        ));
        return workRepository.save(Work.create(
                company.getId(), customer.getId(), "Work " + UUID.randomUUID(), null,
                WorkExecutionLocationType.FINAL_CUSTOMER_LOCATION, null, WorkStatus.ACTIVE, null, null, null
        ));
    }

    private String nextLegalDocument() {
        return String.format("%014d", Math.floorMod(UUID.randomUUID().getLeastSignificantBits(), 100_000_000_000_000L));
    }
}
