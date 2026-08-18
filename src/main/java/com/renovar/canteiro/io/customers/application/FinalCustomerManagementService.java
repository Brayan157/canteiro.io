package com.renovar.canteiro.io.customers.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.access.domain.ChangeOperation;
import com.renovar.canteiro.io.customers.domain.FinalCustomer;
import com.renovar.canteiro.io.customers.domain.FinalCustomerAddress;
import com.renovar.canteiro.io.customers.domain.FinalCustomerAddressRepository;
import com.renovar.canteiro.io.customers.domain.FinalCustomerContact;
import com.renovar.canteiro.io.customers.domain.FinalCustomerContactRepository;
import com.renovar.canteiro.io.customers.domain.FinalCustomerRepository;
import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.governance.application.ChangeRequestService;
import com.renovar.canteiro.io.governance.application.CreateChangeRequestCommand;
import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.AuditPayload;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.governance.domain.ChangeRequestOperation;
import com.renovar.canteiro.io.governance.domain.ChangeRequestSnapshot;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FinalCustomerManagementService {
    private final TenantContextHolder tenantContextHolder;
    private final AccessAuthorizationService authorizationService;
    private final FinalCustomerRepository customerRepository;
    private final FinalCustomerContactRepository contactRepository;
    private final FinalCustomerAddressRepository addressRepository;
    private final ChangeRequestService changeRequestService;
    private final AuditEventRecorder auditEventRecorder;

    @Transactional
    public FinalCustomerChangeResult create(CreateFinalCustomerCommand command) {
        var tenant = tenantContextHolder.requireCurrentTenant();
        ChangeAuthorizationMode mode = authorizationService.requireChangeAuthorization(AccessModule.CUSTOMERS, ChangeOperation.CREATE);
        if (mode == ChangeAuthorizationMode.REQUEST_APPROVAL) {
            ChangeRequest request = changeRequestService.create(new CreateChangeRequestCommand(AuditModule.CUSTOMERS,
                    ChangeRequestOperation.CREATE, "FinalCustomer", null, 0,
                    new ChangeRequestSnapshot(null, new AuditPayload(proposal(command))), command.justification()));
            return new FinalCustomerChangeResult(null, request, mode);
        }
        FinalCustomer customer = persist(tenant.companyId(), command);
        auditEventRecorder.recordDirectAction(AuditModule.CUSTOMERS, AuditAction.CREATE, "FinalCustomer", customer.getId(),
                null, auditData(customer), Map.of("origin", "direct"));
        return new FinalCustomerChangeResult(customer, null, mode);
    }

    @Transactional(readOnly = true)
    public FinalCustomer find(UUID customerId) {
        authorizationService.requirePermission(AccessModule.CUSTOMERS, AccessAction.READ);
        return customerRepository.findByIdAndCompanyId(customerId, tenantContextHolder.requireCurrentTenant().companyId())
                .orElseThrow(() -> new TenantResourceNotFoundException("Final customer"));
    }

    @Transactional(readOnly = true)
    public Page<FinalCustomer> findAll(Pageable pageable) {
        authorizationService.requirePermission(AccessModule.CUSTOMERS, AccessAction.READ);
        return customerRepository.findByCompanyId(tenantContextHolder.requireCurrentTenant().companyId(), pageable);
    }

    @Transactional
    public void applyApprovedCreation(ChangeRequest request) {
        persist(request.getCompanyId(), fromProposal(request.getSnapshot().proposedData().values()));
    }

    private FinalCustomer persist(UUID companyId, CreateFinalCustomerCommand command) {
        FinalCustomer customer = customerRepository.save(FinalCustomer.create(companyId, command.customerType(), command.name(), command.document()));
        for (CreateFinalCustomerCommand.Contact contact : command.contacts() == null ? List.<CreateFinalCustomerCommand.Contact>of() : command.contacts()) {
            contactRepository.save(FinalCustomerContact.create(companyId, customer.getId(), contact.name(), contact.email(), contact.phone(), contact.primaryContact()));
        }
        for (CreateFinalCustomerCommand.Address address : command.addresses() == null ? List.<CreateFinalCustomerCommand.Address>of() : command.addresses()) {
            addressRepository.save(FinalCustomerAddress.create(companyId, customer.getId(), address.label(), address.postalCode(), address.street(), address.number(), address.complement(), address.district(), address.city(), address.state(), address.country(), address.primaryAddress()));
        }
        return customer;
    }

    private Map<String, Object> proposal(CreateFinalCustomerCommand command) {
        return Map.of("customerType", command.customerType().name(), "name", command.name(), "document", command.document(), "contacts", command.contacts() == null ? List.of() : command.contacts(), "addresses", command.addresses() == null ? List.of() : command.addresses());
    }

    @SuppressWarnings("unchecked")
    private CreateFinalCustomerCommand fromProposal(Map<String, Object> data) {
        var type = com.renovar.canteiro.io.customers.domain.FinalCustomerType.valueOf((String) data.get("customerType"));
        List<CreateFinalCustomerCommand.Contact> contacts = ((List<Map<String, Object>>) data.getOrDefault("contacts", List.of())).stream().map(c -> new CreateFinalCustomerCommand.Contact((String) c.get("name"), (String) c.get("email"), (String) c.get("phone"), Boolean.TRUE.equals(c.get("primaryContact")))).toList();
        List<CreateFinalCustomerCommand.Address> addresses = ((List<Map<String, Object>>) data.getOrDefault("addresses", List.of())).stream().map(a -> new CreateFinalCustomerCommand.Address((String) a.get("label"), (String) a.get("postalCode"), (String) a.get("street"), (String) a.get("number"), (String) a.get("complement"), (String) a.get("district"), (String) a.get("city"), (String) a.get("state"), (String) a.get("country"), Boolean.TRUE.equals(a.get("primaryAddress")))).toList();
        return new CreateFinalCustomerCommand(type, (String) data.get("name"), (String) data.get("document"), contacts, addresses, null);
    }

    private Map<String, Object> auditData(FinalCustomer customer) { return Map.of("name", customer.getName(), "customerType", customer.getCustomerType().name(), "active", customer.isActive()); }
}
