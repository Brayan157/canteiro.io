package com.renovar.canteiro.io.receivables;

import com.renovar.canteiro.io.AbstractPostgresIntegrationTest;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.contracts.domain.Contract;
import com.renovar.canteiro.io.contracts.domain.ContractBillingControlFilter;
import com.renovar.canteiro.io.contracts.application.ApprovedContractBillingAmountProvider;
import com.renovar.canteiro.io.contracts.domain.ContractDiscount;
import com.renovar.canteiro.io.contracts.domain.ContractDiscountRepository;
import com.renovar.canteiro.io.contracts.domain.ContractRepository;
import com.renovar.canteiro.io.contracts.domain.ContractService;
import com.renovar.canteiro.io.contracts.domain.ContractServiceRepository;
import com.renovar.canteiro.io.contracts.domain.ContractServiceStatus;
import com.renovar.canteiro.io.contracts.domain.ContractStatus;
import com.renovar.canteiro.io.contracts.domain.DiscountType;
import com.renovar.canteiro.io.customers.domain.FinalCustomer;
import com.renovar.canteiro.io.customers.domain.FinalCustomerRepository;
import com.renovar.canteiro.io.customers.domain.FinalCustomerType;
import com.renovar.canteiro.io.platform.company.domain.Company;
import com.renovar.canteiro.io.platform.company.domain.CompanyRepository;
import com.renovar.canteiro.io.receivables.domain.BillingAllocation;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationRepository;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationType;
import com.renovar.canteiro.io.receivables.domain.BillingInstallment;
import com.renovar.canteiro.io.receivables.domain.BillingInstallmentRepository;
import com.renovar.canteiro.io.receivables.domain.Receivable;
import com.renovar.canteiro.io.receivables.domain.ReceivableRepository;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlement;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementEntryType;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementPaymentMethod;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementRepository;
import com.renovar.canteiro.io.receivables.domain.ReceivableStatus;
import com.renovar.canteiro.io.receivables.domain.TaxObligation;
import com.renovar.canteiro.io.receivables.domain.TaxObligationRepository;
import com.renovar.canteiro.io.receivables.domain.TaxTreatment;
import com.renovar.canteiro.io.payables.domain.Payable;
import com.renovar.canteiro.io.payables.domain.PayableRepository;
import com.renovar.canteiro.io.receivables.domain.Invoice;
import com.renovar.canteiro.io.receivables.domain.InvoiceAttachment;
import com.renovar.canteiro.io.receivables.domain.InvoiceAttachmentRepository;
import com.renovar.canteiro.io.receivables.domain.InvoiceAttachmentType;
import com.renovar.canteiro.io.receivables.domain.InvoiceItem;
import com.renovar.canteiro.io.receivables.domain.InvoiceItemRepository;
import com.renovar.canteiro.io.receivables.domain.InvoiceRepository;
import com.renovar.canteiro.io.receivables.application.CalculateContractBillingBalanceUseCase;
import com.renovar.canteiro.io.receivables.application.CalculateInvoiceAllocationBalanceUseCase;
import com.renovar.canteiro.io.receivables.application.CalculateReceivableBalanceUseCase;
import com.renovar.canteiro.io.receivables.application.BillingLimitExceededException;
import com.renovar.canteiro.io.receivables.application.ValidateContractBillingLimitUseCase;
import com.renovar.canteiro.io.works.domain.Work;
import com.renovar.canteiro.io.works.domain.WorkExecutionLocationType;
import com.renovar.canteiro.io.works.domain.WorkRepository;
import com.renovar.canteiro.io.works.domain.WorkStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BillingPersistenceIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private FinalCustomerRepository finalCustomerRepository;
    @Autowired
    private WorkRepository workRepository;
    @Autowired
    private ContractRepository contractRepository;
    @Autowired
    private ContractDiscountRepository contractDiscountRepository;
    @Autowired
    private ContractServiceRepository contractServiceRepository;
    @Autowired
    private BillingInstallmentRepository billingInstallmentRepository;
    @Autowired
    private BillingAllocationRepository billingAllocationRepository;
    @Autowired
    private ReceivableRepository receivableRepository;
    @Autowired
    private ReceivableSettlementRepository receivableSettlementRepository;
    @Autowired
    private TaxObligationRepository taxObligationRepository;
    @Autowired
    private PayableRepository payableRepository;
    @Autowired
    private InvoiceRepository invoiceRepository;
    @Autowired
    private InvoiceAttachmentRepository invoiceAttachmentRepository;
    @Autowired
    private InvoiceItemRepository invoiceItemRepository;
    @Autowired
    private CalculateContractBillingBalanceUseCase calculateContractBillingBalanceUseCase;
    @Autowired
    private CalculateInvoiceAllocationBalanceUseCase calculateInvoiceAllocationBalanceUseCase;
    @Autowired
    private CalculateReceivableBalanceUseCase calculateReceivableBalanceUseCase;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ApprovedContractBillingAmountProvider approvedContractBillingAmountProvider;
    @Autowired
    private ValidateContractBillingLimitUseCase validateContractBillingLimitUseCase;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void persistsInvoiceAttachmentMetadataWithinTheInvoiceTenantAndContract() {
        Company company = createCompany("invoice-attachment");
        Contract contract = createContract(company, "Invoice attachment contract");
        Invoice invoice = invoiceRepository.save(Invoice.register(company.getId(), contract.getId(), "NF-ATT-001", null,
                null, LocalDate.of(2026, 8, 20), new BigDecimal("100.00")));

        InvoiceAttachment attachment = invoiceAttachmentRepository.save(InvoiceAttachment.create(company.getId(), invoice.getId(),
                contract.getId(), InvoiceAttachmentType.PDF, "nota.pdf", "application/pdf", 12,
                "a".repeat(64), "invoices/test/nota.pdf", UUID.randomUUID()));

        assertEquals(invoice.getId(), invoiceAttachmentRepository.findByIdAndCompanyId(attachment.getId(), company.getId())
                .orElseThrow().getInvoiceId());
        assertTrue(invoiceAttachmentRepository.findByIdAndCompanyId(attachment.getId(), UUID.randomUUID()).isEmpty());
    }

    @Test
    void persistsAnInstallmentAndItsServiceAllocationWithinTheSameContractAndTenant() {
        Company company = createCompany("billing");
        Contract contract = createContract(company, "First contract");
        ContractService service = contractServiceRepository.save(ContractService.create(
                company.getId(), contract.getId(), "Assembly", null, ContractServiceStatus.ACTIVE,
                BigDecimal.ONE, new BigDecimal("200.00")
        ));
        BillingInstallment installment = billingInstallmentRepository.save(BillingInstallment.create(
                company.getId(), contract.getId(), "FAT-001", "First billing", LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 15)
        ));
        BillingAllocation allocation = billingAllocationRepository.save(BillingAllocation.create(
                company.getId(), installment.getId(), contract.getId(), service.getId(), new BigDecimal("200.00")
        ));

        assertEquals(contract.getId(), billingInstallmentRepository
                .findByIdAndCompanyId(installment.getId(), company.getId()).orElseThrow().getContractId());
        assertEquals(allocation.getId(), billingAllocationRepository
                .findByBillingInstallmentIdAndCompanyId(installment.getId(), company.getId()).getFirst().getId());
        assertFalse(billingInstallmentRepository.findByIdAndCompanyId(installment.getId(), UUID.randomUUID()).isPresent());
    }

    @Test
    void filtersBillingControlContractsWithinTheCurrentCompanyOnly() {
        Company firstCompany = createCompany("billing-control-first");
        Company secondCompany = createCompany("billing-control-second");
        Contract firstContract = createContract(firstCompany, "Electrical installation");
        createContract(secondCompany, "Electrical installation");

        var result = contractRepository.findBillingControl(firstCompany.getId(),
                new ContractBillingControlFilter(null, ContractStatus.OPEN, "electrical"), PageRequest.of(0, 20));

        assertEquals(1, result.getTotalElements());
        assertEquals(firstContract.getId(), result.getContent().getFirst().getId());
    }

    @Test
    void persistsAContractDiscountWithoutBindingItToAService() {
        Company company = createCompany("contract-discount");
        Contract contract = createContract(company, "Discount contract");
        BillingInstallment installment = billingInstallmentRepository.save(BillingInstallment.create(
                company.getId(), contract.getId(), "FAT-002", null, null, LocalDate.of(2026, 8, 15)
        ));

        BillingAllocation discount = billingAllocationRepository.save(BillingAllocation.createContractDiscount(
                company.getId(), installment.getId(), contract.getId(), new BigDecimal("-20.00")
        ));

        assertEquals(BillingAllocationType.CONTRACT_DISCOUNT, discount.getType());
        assertEquals(new BigDecimal("-20.00"), discount.getAllocatedAmount());
        assertNull(discount.getContractServiceId());
    }

    @Test
    void persistsOneIndividualReceivablePerInstallmentWithItsFinancialGroup() {
        Company company = createCompany("receivable");
        Contract contract = createContract(company, "Receivable contract");
        BillingInstallment installment = billingInstallmentRepository.save(BillingInstallment.createApproved(
                company.getId(), contract.getId(), "FAT-006", "Approved installment", LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 15)
        ));
        UUID financialGroupId = UUID.randomUUID();
        Receivable receivable = receivableRepository.save(Receivable.create(
                company.getId(), contract.getId(), installment.getId(), financialGroupId, "REC-006",
                "Receivable for FAT-006", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 15),
                new BigDecimal("80.00")
        ));

        Receivable found = receivableRepository.findByBillingInstallmentIdAndCompanyId(installment.getId(), company.getId())
                .orElseThrow();
        assertEquals(receivable.getId(), found.getId());
        assertEquals(financialGroupId, found.getFinancialGroupId());
        assertFalse(receivableRepository.findByBillingInstallmentIdAndCompanyId(installment.getId(), UUID.randomUUID()).isPresent());
    }

    @Test
    void persistsPartialAndFinalReceivableSettlementsAndCalculatesTheirRemainingBalance() {
        Company company = createCompany("receivable-settlement");
        Contract contract = createContract(company, "Settlement contract");
        BillingInstallment installment = billingInstallmentRepository.save(BillingInstallment.createApproved(
                company.getId(), contract.getId(), "FAT-SET-001", null, LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 15)
        ));
        Receivable receivable = receivableRepository.save(Receivable.create(
                company.getId(), contract.getId(), installment.getId(), UUID.randomUUID(), "REC-SET-001", null,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 15), new BigDecimal("100.00")
        ));
        receivableSettlementRepository.save(ReceivableSettlement.confirm(
                company.getId(), receivable.getId(), contract.getId(), LocalDate.of(2026, 8, 10), new BigDecimal("40.00"),
                ReceivableSettlementPaymentMethod.PIX, null
        ));
        receivableSettlementRepository.save(ReceivableSettlement.confirm(
                company.getId(), receivable.getId(), contract.getId(), LocalDate.of(2026, 8, 16), new BigDecimal("60.00"),
                ReceivableSettlementPaymentMethod.BANK_TRANSFER, "Final settlement"
        ));

        var balance = calculateReceivableBalanceUseCase.calculate(company.getId(), receivable.getId());

        assertEquals(new BigDecimal("100.00"), balance.confirmedSettlementAmount());
        assertEquals(new BigDecimal("0.00"), balance.outstandingAmount());
        assertEquals(2, receivableSettlementRepository.findByReceivableIdAndCompanyId(receivable.getId(), company.getId()).size());
        assertTrue(receivableSettlementRepository.findByReceivableIdAndCompanyId(receivable.getId(), UUID.randomUUID()).isEmpty());
        Receivable partiallyReceived = receivableRepository.updateStatus(receivable.withStatus(ReceivableStatus.PARTIALLY_RECEIVED));
        assertEquals(ReceivableStatus.PARTIALLY_RECEIVED,
                receivableRepository.findByIdAndCompanyId(partiallyReceived.getId(), company.getId()).orElseThrow().getStatus());
    }

    @Test
    void persistsLinkedNegativeReversalAndReopensTheCanonicalReceivableBalance() {
        Company company = createCompany("settlement-reversal");
        Contract contract = createContract(company, "Settlement reversal contract");
        BillingInstallment installment = billingInstallmentRepository.save(BillingInstallment.createApproved(
                company.getId(), contract.getId(), "FAT-REV-001", null, LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 15)));
        Receivable receivable = receivableRepository.save(Receivable.create(
                company.getId(), contract.getId(), installment.getId(), UUID.randomUUID(), "REC-REV-001", null,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 15), new BigDecimal("100.00")));
        ReceivableSettlement original = receivableSettlementRepository.save(ReceivableSettlement.confirm(
                company.getId(), receivable.getId(), contract.getId(), LocalDate.of(2026, 8, 10),
                new BigDecimal("100.00"), ReceivableSettlementPaymentMethod.PIX, null));

        ReceivableSettlement reversal = receivableSettlementRepository.save(ReceivableSettlement.reverse(
                original, LocalDate.of(2026, 8, 20), "Duplicate receipt"));
        var balance = calculateReceivableBalanceUseCase.calculate(company.getId(), receivable.getId());

        assertEquals(ReceivableSettlementEntryType.REVERSAL, reversal.getEntryType());
        assertEquals(original.getId(), reversal.getReversalOfSettlementId());
        assertEquals(new BigDecimal("-100.00"), reversal.getAmount());
        assertEquals(new BigDecimal("0.00"), balance.confirmedSettlementAmount());
        assertEquals(new BigDecimal("100.00"), balance.outstandingAmount());
        assertThrows(DataIntegrityViolationException.class, () -> receivableSettlementRepository.save(
                ReceivableSettlement.reverse(original, LocalDate.of(2026, 8, 21), "Repeated reversal")));
    }

    @Test
    void persistsTaxTreatmentsAndDerivesWithholdingFromTheReceivableBalance() {
        Company company = createCompany("tax");
        Contract contract = createContract(company, "Tax contract");
        BillingInstallment installment = billingInstallmentRepository.save(BillingInstallment.createApproved(
                company.getId(), contract.getId(), "FAT-TAX-001", null, LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 15)
        ));
        Receivable receivable = receivableRepository.save(Receivable.create(
                company.getId(), contract.getId(), installment.getId(), UUID.randomUUID(), "REC-TAX-001", null,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 15), new BigDecimal("100.00")
        ));
        Invoice invoice = invoiceRepository.save(Invoice.register(
                company.getId(), contract.getId(), "NF-TAX-001", null, null, LocalDate.of(2026, 8, 20),
                new BigDecimal("100.00")
        ));
        TaxObligation payableTax = taxObligationRepository.save(TaxObligation.register(
                company.getId(), invoice.getId(), contract.getId(), null, "ISS", null, LocalDate.of(2026, 8, 30),
                new BigDecimal("10.00"), TaxTreatment.PAYABLE_BY_COMPANY
        ));
        Payable payable = payableRepository.save(Payable.createTaxPayable(
                company.getId(), contract.getWorkId(), contract.getId(), payableTax.getId(), "TAX-NF-TAX-001", "ISS",
                LocalDate.of(2026, 8, 30), new BigDecimal("10.00")
        ));
        taxObligationRepository.save(TaxObligation.register(
                company.getId(), invoice.getId(), contract.getId(), receivable.getId(), "IRRF", null, null,
                new BigDecimal("15.00"), TaxTreatment.WITHHELD_BY_CUSTOMER
        ));
        taxObligationRepository.save(TaxObligation.register(
                company.getId(), invoice.getId(), contract.getId(), null, "INSS", null, null,
                new BigDecimal("3.00"), TaxTreatment.INFORMATIONAL
        ));

        var balance = calculateReceivableBalanceUseCase.calculate(company.getId(), receivable.getId());

        assertEquals(new BigDecimal("15.00"), balance.withheldAmount());
        assertEquals(new BigDecimal("85.00"), balance.netReceivableAmount());
        assertEquals(new BigDecimal("85.00"), balance.outstandingAmount());
        assertEquals(payable.getId(), payableRepository.findByTaxObligationIdAndCompanyId(payableTax.getId(), company.getId())
                .orElseThrow().getId());
        assertFalse(taxObligationRepository.findByIdAndCompanyId(payableTax.getId(), UUID.randomUUID()).isPresent());
    }

    @Test
    void persistsInvoiceItemsForOnlyOneContractAndTheAllocationTenant() {
        Company company = createCompany("invoice");
        Contract contract = createContract(company, "Invoice contract");
        ContractService service = contractServiceRepository.save(ContractService.create(
                company.getId(), contract.getId(), "Invoice service", null, ContractServiceStatus.ACTIVE,
                BigDecimal.ONE, new BigDecimal("150.00")
        ));
        BillingInstallment installment = billingInstallmentRepository.save(BillingInstallment.createApproved(
                company.getId(), contract.getId(), "FAT-INV-001", null, LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 15)
        ));
        BillingAllocation allocation = billingAllocationRepository.save(BillingAllocation.createService(
                company.getId(), installment.getId(), contract.getId(), service.getId(), new BigDecimal("150.00")
        ).activate());
        Invoice invoice = invoiceRepository.save(Invoice.register(
                company.getId(), contract.getId(), "NF-001", "1", null, LocalDate.of(2026, 8, 20),
                new BigDecimal("150.00")
        ));
        InvoiceItem item = invoiceItemRepository.save(InvoiceItem.create(
                company.getId(), invoice.getId(), contract.getId(), allocation.getId(), service.getId(),
                "Service billed in NF", new BigDecimal("150.00")
        ));

        assertEquals(invoice.getId(), invoiceRepository.findByIdAndCompanyId(invoice.getId(), company.getId())
                .orElseThrow().getId());
        assertEquals(item.getId(), invoiceItemRepository.findByInvoiceIdAndCompanyId(invoice.getId(), company.getId())
                .getFirst().getId());
        assertEquals(new BigDecimal("150.00"), invoiceItemRepository
                .registeredAmountByBillingAllocationIdAndCompanyId(allocation.getId(), company.getId()));
        assertFalse(invoiceRepository.findByIdAndCompanyId(invoice.getId(), UUID.randomUUID()).isPresent());
    }

    @Test
    void calculatesOneServiceAllocationBalanceAcrossSeveralInvoices() {
        Company company = createCompany("partial-invoice");
        Contract contract = createContract(company, "Partial invoice contract");
        ContractService service = contractServiceRepository.save(ContractService.create(
                company.getId(), contract.getId(), "Partial invoice service", null, ContractServiceStatus.ACTIVE,
                BigDecimal.ONE, new BigDecimal("100.00")
        ));
        BillingInstallment installment = billingInstallmentRepository.save(BillingInstallment.createApproved(
                company.getId(), contract.getId(), "FAT-INV-003", null, LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 15)
        ));
        BillingAllocation allocation = billingAllocationRepository.save(BillingAllocation.createService(
                company.getId(), installment.getId(), contract.getId(), service.getId(), new BigDecimal("100.00")
        ).activate());
        Invoice firstInvoice = invoiceRepository.save(Invoice.register(
                company.getId(), contract.getId(), "NF-003", null, null, LocalDate.of(2026, 8, 20),
                new BigDecimal("40.00")
        ));
        invoiceItemRepository.save(InvoiceItem.create(
                company.getId(), firstInvoice.getId(), contract.getId(), allocation.getId(), service.getId(),
                null, new BigDecimal("40.00")
        ));
        Invoice secondInvoice = invoiceRepository.save(Invoice.register(
                company.getId(), contract.getId(), "NF-004", null, null, LocalDate.of(2026, 8, 21),
                new BigDecimal("60.00")
        ));
        invoiceItemRepository.save(InvoiceItem.create(
                company.getId(), secondInvoice.getId(), contract.getId(), allocation.getId(), service.getId(),
                null, new BigDecimal("60.00")
        ));

        var balance = calculateInvoiceAllocationBalanceUseCase.calculate(company.getId(), allocation.getId());

        assertEquals(new BigDecimal("100.00"), balance.allocatedAmount());
        assertEquals(new BigDecimal("100.00"), balance.registeredInvoiceAmount());
        assertEquals(new BigDecimal("0.00"), balance.availableInvoiceAmount());
    }

    @Test
    void releasesInvoiceAllocationCapacityWhenTheInvoiceAndItsItemAreCancelled() {
        Company company = createCompany("cancelled-invoice-item");
        Contract contract = createContract(company, "Cancelled invoice item contract");
        ContractService service = contractServiceRepository.save(ContractService.create(
                company.getId(), contract.getId(), "Service", null, ContractServiceStatus.ACTIVE,
                BigDecimal.ONE, new BigDecimal("100.00")));
        BillingInstallment installment = billingInstallmentRepository.save(BillingInstallment.createApproved(
                company.getId(), contract.getId(), "FAT-CAN-INV", null, LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 15)));
        BillingAllocation allocation = billingAllocationRepository.save(BillingAllocation.createService(
                company.getId(), installment.getId(), contract.getId(), service.getId(), new BigDecimal("100.00")).activate());
        Invoice invoice = invoiceRepository.save(Invoice.register(company.getId(), contract.getId(), "NF-CAN-INV", null,
                null, LocalDate.of(2026, 8, 20), new BigDecimal("100.00")));
        InvoiceItem item = invoiceItemRepository.save(InvoiceItem.create(company.getId(), invoice.getId(), contract.getId(),
                allocation.getId(), service.getId(), "Service", new BigDecimal("100.00")));

        invoiceItemRepository.updateStatus(item.cancel());
        invoiceRepository.updateStatus(invoice.cancel());
        var balance = calculateInvoiceAllocationBalanceUseCase.calculate(company.getId(), allocation.getId());

        assertEquals(new BigDecimal("0.00"), balance.registeredInvoiceAmount());
        assertEquals(new BigDecimal("100.00"), balance.availableInvoiceAmount());
    }

    @Test
    void reversesAPartialSettlementWithoutErasingTheOriginalReceipt() {
        Company company = createCompany("partial-settlement-reversal");
        Contract contract = createContract(company, "Partial settlement reversal contract");
        BillingInstallment installment = billingInstallmentRepository.save(BillingInstallment.createApproved(
                company.getId(), contract.getId(), "FAT-PART-REV", null, LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 15)));
        Receivable receivable = receivableRepository.save(Receivable.create(company.getId(), contract.getId(), installment.getId(),
                UUID.randomUUID(), "REC-PART-REV", null, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 15),
                new BigDecimal("100.00")));
        ReceivableSettlement partial = receivableSettlementRepository.save(ReceivableSettlement.confirm(company.getId(),
                receivable.getId(), contract.getId(), LocalDate.of(2026, 8, 10), new BigDecimal("40.00"),
                ReceivableSettlementPaymentMethod.PIX, "Partial receipt"));

        ReceivableSettlement reversal = receivableSettlementRepository.save(ReceivableSettlement.reverse(partial,
                LocalDate.of(2026, 8, 11), "Receipt reversed"));
        var balance = calculateReceivableBalanceUseCase.calculate(company.getId(), receivable.getId());

        assertEquals(new BigDecimal("-40.00"), reversal.getAmount());
        assertEquals(partial.getId(), reversal.getReversalOfSettlementId());
        assertEquals(2, receivableSettlementRepository.findByReceivableIdAndCompanyId(receivable.getId(), company.getId()).size());
        assertEquals(new BigDecimal("0.00"), balance.confirmedSettlementAmount());
        assertEquals(new BigDecimal("100.00"), balance.outstandingAmount());
    }

    @Test
    void rejectsInvoiceItemWithAnAllocationFromAnotherContract() {
        Company company = createCompany("invoice-contract-chain");
        Contract firstContract = createContract(company, "First invoice contract");
        Contract secondContract = contractRepository.save(Contract.create(
                company.getId(), firstContract.getWorkId(), null, "Second invoice contract", ContractStatus.OPEN,
                null, null, null
        ));
        ContractService service = contractServiceRepository.save(ContractService.create(
                company.getId(), secondContract.getId(), "Other service", null, ContractServiceStatus.ACTIVE,
                BigDecimal.ONE, new BigDecimal("100.00")
        ));
        BillingInstallment installment = billingInstallmentRepository.save(BillingInstallment.createApproved(
                company.getId(), secondContract.getId(), "FAT-INV-002", null, LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 15)
        ));
        BillingAllocation allocation = billingAllocationRepository.save(BillingAllocation.createService(
                company.getId(), installment.getId(), secondContract.getId(), service.getId(), new BigDecimal("100.00")
        ).activate());
        Invoice invoice = invoiceRepository.save(Invoice.register(
                company.getId(), firstContract.getId(), "NF-002", null, null, LocalDate.of(2026, 8, 20),
                new BigDecimal("100.00")
        ));

        assertThrows(DataIntegrityViolationException.class, () -> invoiceItemRepository.save(InvoiceItem.create(
                company.getId(), invoice.getId(), firstContract.getId(), allocation.getId(), service.getId(),
                null, new BigDecimal("100.00")
        )));
    }

    @Test
    void calculatesTheCanonicalBalanceFromActiveAllocationsInTheSameTenantOnly() {
        Company company = createCompany("balance");
        Contract contract = createContract(company, "Balance contract");
        ContractService service = contractServiceRepository.save(ContractService.create(
                company.getId(), contract.getId(), "Service", null, ContractServiceStatus.ACTIVE,
                BigDecimal.ONE, new BigDecimal("200.00")
        ));
        BillingInstallment installment = billingInstallmentRepository.save(BillingInstallment.create(
                company.getId(), contract.getId(), "FAT-003", null, null, LocalDate.of(2026, 8, 15)
        ));
        BillingAllocation activeAllocation = billingAllocationRepository.save(BillingAllocation.createService(
                company.getId(), installment.getId(), contract.getId(), service.getId(), new BigDecimal("120.00")
        ));
        billingAllocationRepository.save(BillingAllocation.createService(
                company.getId(), installment.getId(), contract.getId(), service.getId(), new BigDecimal("30.00")
        ));
        jdbcTemplate.update("UPDATE billing_allocation SET status = 'ACTIVE' WHERE id = ?", activeAllocation.getId());

        var balance = calculateContractBillingBalanceUseCase.calculate(company.getId(), contract.getId());

        assertEquals(new BigDecimal("200.00"), balance.contractNetAmount());
        assertEquals(new BigDecimal("120.00"), balance.activeAllocatedAmount());
        assertEquals(new BigDecimal("80.00"), balance.availableAmount());
        assertEquals(new BigDecimal("120.00"), approvedContractBillingAmountProvider
                .approvedNetAmount(company.getId(), contract.getId()));
        assertThrows(TenantResourceNotFoundException.class, () -> calculateContractBillingBalanceUseCase
                .calculate(UUID.randomUUID(), contract.getId()));
    }

    @Test
    void updatesTheSingleContractDiscountRow() {
        Company company = createCompany("discount-update");
        Contract contract = createContract(company, "Discount update contract");

        ContractDiscount first = contractDiscountRepository.save(ContractDiscount.create(
                company.getId(), contract.getId(), DiscountType.FIXED, new BigDecimal("5.00")));
        ContractDiscount updated = contractDiscountRepository.save(ContractDiscount.create(
                company.getId(), contract.getId(), DiscountType.PERCENTAGE, new BigDecimal("10.0000")));

        assertEquals(first.getId(), updated.getId());
        assertEquals(DiscountType.PERCENTAGE, updated.getDiscountType());
        assertEquals(new BigDecimal("10.0000"), updated.getDiscountValue());
    }

    @Test
    void rejectsNegativeBillingAllocationWithoutContractAdjustmentBacking() {
        Company company = createCompany("discount-backing");
        Contract contract = createContract(company, "Discount backing contract");
        ContractService service = contractServiceRepository.save(ContractService.create(
                company.getId(), contract.getId(), "Service", null, ContractServiceStatus.ACTIVE,
                BigDecimal.ONE, new BigDecimal("100.00")));
        BillingInstallment installment = billingInstallmentRepository.save(BillingInstallment.createApproved(
                company.getId(), contract.getId(), "FAT-DISCOUNT", null, LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 15)));
        BillingAllocation serviceAllocation = BillingAllocation.createService(
                company.getId(), installment.getId(), contract.getId(), service.getId(), new BigDecimal("100.00"));
        BillingAllocation unsupportedDiscount = BillingAllocation.createContractDiscount(
                company.getId(), installment.getId(), contract.getId(), new BigDecimal("-10.00"));

        assertThrows(BillingLimitExceededException.class, () -> transactionTemplate.executeWithoutResult(status ->
                validateContractBillingLimitUseCase.requireAvailableForActivation(
                        company.getId(), contract.getId(), List.of(serviceAllocation, unsupportedDiscount))
        ));
    }

    @Test
    void blocksOneOfTwoConcurrentReservationsThatWouldExceedTheContractBalance() throws Exception {
        Company company = createCompany("concurrency");
        Contract contract = createContract(company, "Concurrent contract");
        ContractService service = contractServiceRepository.save(ContractService.create(
                company.getId(), contract.getId(), "Service", null, ContractServiceStatus.ACTIVE,
                BigDecimal.ONE, new BigDecimal("100.00")
        ));
        BillingInstallment firstInstallment = billingInstallmentRepository.save(BillingInstallment.create(
                company.getId(), contract.getId(), "FAT-004", null, null, LocalDate.of(2026, 8, 15)
        ));
        BillingInstallment secondInstallment = billingInstallmentRepository.save(BillingInstallment.create(
                company.getId(), contract.getId(), "FAT-005", null, null, LocalDate.of(2026, 8, 16)
        ));
        BillingAllocation first = BillingAllocation.createService(
                company.getId(), firstInstallment.getId(), contract.getId(), service.getId(), new BigDecimal("60.00")
        );
        BillingAllocation second = BillingAllocation.createService(
                company.getId(), secondInstallment.getId(), contract.getId(), service.getId(), new BigDecimal("60.00")
        );
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> firstResult = executor.submit(() -> reserve(company.getId(), contract.getId(), first, ready, start));
            Future<Boolean> secondResult = executor.submit(() -> reserve(company.getId(), contract.getId(), second, ready, start));
            ready.await();
            start.countDown();

            assertEquals(1, (firstResult.get() ? 1 : 0) + (secondResult.get() ? 1 : 0));
        }

        var balance = calculateContractBillingBalanceUseCase.calculate(company.getId(), contract.getId());
        assertEquals(new BigDecimal("60.00"), balance.activeAllocatedAmount());
        assertEquals(new BigDecimal("40.00"), balance.availableAmount());
    }

    @Test
    void blocksOneOfTwoConcurrentInvoiceItemsThatWouldExceedTheSameAllocation() throws Exception {
        Company company = createCompany("invoice-concurrency");
        Contract contract = createContract(company, "Concurrent invoice contract");
        ContractService service = contractServiceRepository.save(ContractService.create(
                company.getId(), contract.getId(), "Service", null, ContractServiceStatus.ACTIVE,
                BigDecimal.ONE, new BigDecimal("100.00")));
        BillingInstallment installment = billingInstallmentRepository.save(BillingInstallment.createApproved(
                company.getId(), contract.getId(), "FAT-CON-NF", null, LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 15)));
        BillingAllocation allocation = billingAllocationRepository.save(BillingAllocation.createService(
                company.getId(), installment.getId(), contract.getId(), service.getId(), new BigDecimal("100.00")).activate());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> registerInvoiceItem(company.getId(), contract.getId(), allocation,
                    service.getId(), "NF-CON-1", ready, start));
            Future<Boolean> second = executor.submit(() -> registerInvoiceItem(company.getId(), contract.getId(), allocation,
                    service.getId(), "NF-CON-2", ready, start));
            ready.await();
            start.countDown();

            assertEquals(1, (first.get() ? 1 : 0) + (second.get() ? 1 : 0));
        }

        var balance = calculateInvoiceAllocationBalanceUseCase.calculate(company.getId(), allocation.getId());
        assertEquals(new BigDecimal("60.00"), balance.registeredInvoiceAmount());
        assertEquals(new BigDecimal("40.00"), balance.availableInvoiceAmount());
    }

    @Test
    void rejectsAnAllocationForAServiceFromAnotherContract() {
        Company company = createCompany("contract-chain");
        Contract installmentContract = createContract(company, "Installment contract");
        Contract serviceContract = contractRepository.save(Contract.create(
                company.getId(), installmentContract.getWorkId(), null, "Service contract", ContractStatus.OPEN,
                null, null, null
        ));
        ContractService service = contractServiceRepository.save(ContractService.create(
                company.getId(), serviceContract.getId(), "Service", null, ContractServiceStatus.ACTIVE,
                BigDecimal.ONE, new BigDecimal("100.00")
        ));
        BillingInstallment installment = billingInstallmentRepository.save(BillingInstallment.create(
                company.getId(), installmentContract.getId(), null, null, null, LocalDate.of(2026, 8, 15)
        ));

        assertThrows(DataIntegrityViolationException.class, () -> billingAllocationRepository.save(BillingAllocation.create(
                company.getId(), installment.getId(), installmentContract.getId(), service.getId(), new BigDecimal("100.00")
        )));
    }

    private Contract createContract(Company company, String contractName) {
        FinalCustomer customer = finalCustomerRepository.save(FinalCustomer.create(
                company.getId(), FinalCustomerType.LEGAL, contractName + " customer",
                "12345678000190"
        ));
        Work work = workRepository.save(Work.create(
                company.getId(), customer.getId(), contractName + " work", null,
                WorkExecutionLocationType.FINAL_CUSTOMER_LOCATION, null, WorkStatus.ACTIVE, null, null, null
        ));
        return contractRepository.save(Contract.create(
                company.getId(), work.getId(), null, contractName, ContractStatus.OPEN, null, null, null
        ));
    }

    private boolean reserve(UUID companyId, UUID contractId, BillingAllocation allocation, CountDownLatch ready,
                            CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            transactionTemplate.executeWithoutResult(status -> {
                validateContractBillingLimitUseCase.requireAvailableForActivation(
                        companyId, contractId, List.of(allocation)
                );
                billingAllocationRepository.save(allocation.activate());
            });
            return true;
        } catch (BillingLimitExceededException | OptimisticLockingFailureException exception) {
            return false;
        }
    }

    private boolean registerInvoiceItem(UUID companyId, UUID contractId, BillingAllocation allocation, UUID serviceId,
                                        String number, CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            transactionTemplate.executeWithoutResult(status -> {
                contractRepository.findByIdAndCompanyIdWithBillingLock(contractId, companyId).orElseThrow();
                var allocationBalance = calculateInvoiceAllocationBalanceUseCase.calculate(companyId, allocation.getId());
                if (!allocationBalance.canRegister(new BigDecimal("60.00"))) {
                    throw new IllegalStateException("Invoice item exceeds allocation capacity");
                }
                Invoice invoice = invoiceRepository.save(Invoice.register(companyId, contractId, number, null, null,
                        LocalDate.of(2026, 8, 20), new BigDecimal("60.00")));
                invoiceItemRepository.save(InvoiceItem.create(companyId, invoice.getId(), contractId, allocation.getId(),
                        serviceId, "Concurrent item", new BigDecimal("60.00")));
            });
            return true;
        } catch (OptimisticLockingFailureException | IllegalStateException exception) {
            return false;
        }
    }

    private Company createCompany(String suffix) {
        String value = UUID.randomUUID().toString();
        return companyRepository.save(Company.create(
                "Company " + suffix + value, suffix, Long.toUnsignedString(UUID.randomUUID().getLeastSignificantBits()),
                suffix + value + "@example.com", null, null, null
        ));
    }
}
