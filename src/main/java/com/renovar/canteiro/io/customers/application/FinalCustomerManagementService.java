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
import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;

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
    public Page<FinalCustomer> findAll(FinalCustomerFilter filter, Pageable pageable) {
        authorizationService.requirePermission(AccessModule.CUSTOMERS, AccessAction.READ);
        return customerRepository.findByCompanyId(tenantContextHolder.requireCurrentTenant().companyId(), filter, pageable);
    }

    @Transactional
    public FinalCustomerChangeResult update(UUID customerId, UpdateFinalCustomerCommand command) {
        var tenant = tenantContextHolder.requireCurrentTenant();
        FinalCustomer current = customerRepository.findByIdAndCompanyId(customerId, tenant.companyId())
                .orElseThrow(() -> new TenantResourceNotFoundException("Final customer"));
        ChangeAuthorizationMode mode = authorizationService.requireChangeAuthorization(AccessModule.CUSTOMERS, ChangeOperation.UPDATE);
        FinalCustomer proposed = current.updateProfile(command.customerType(), command.name(), command.document());
        ensureDocumentAvailable(tenant.companyId(), proposed.getDocument(), current.getId());
        if (mode == ChangeAuthorizationMode.REQUEST_APPROVAL) {
            ChangeRequest request = changeRequestService.create(new CreateChangeRequestCommand(AuditModule.CUSTOMERS,
                    ChangeRequestOperation.UPDATE, "FinalCustomer", current.getId(), 0,
                    new ChangeRequestSnapshot(new AuditPayload(auditData(current)), new AuditPayload(proposal(command))), command.justification()));
            return new FinalCustomerChangeResult(null, request, mode);
        }
        FinalCustomer saved = customerRepository.save(proposed);
        auditEventRecorder.recordDirectAction(AuditModule.CUSTOMERS, AuditAction.UPDATE, "FinalCustomer", saved.getId(),
                auditData(current), auditData(saved), Map.of("origin", "direct"));
        return new FinalCustomerChangeResult(saved, null, mode);
    }

    @Transactional(readOnly = true)
    public List<FinalCustomerContact> contacts(UUID customerId) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        authorizationService.requirePermission(AccessModule.CUSTOMERS, AccessAction.READ);
        findCustomer(customerId, companyId);
        return contactRepository.findByFinalCustomerIdAndCompanyId(customerId, companyId).stream()
                .filter(FinalCustomerContact::isActive)
                .sorted(java.util.Comparator.comparing(FinalCustomerContact::isPrimaryContact).reversed()
                        .thenComparing(FinalCustomerContact::getCreatedAt, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .toList();
    }

    @Transactional
    public FinalCustomerContactChangeResult createContact(UUID customerId, FinalCustomerContactCommand command) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        findCustomer(customerId, companyId);
        validateContact(command, companyId, customerId);
        ChangeAuthorizationMode mode = authorizationService.requireChangeAuthorization(AccessModule.CUSTOMERS, ChangeOperation.CREATE);
        if (mode == ChangeAuthorizationMode.REQUEST_APPROVAL) {
            ChangeRequest request = createContactChangeRequest(ChangeRequestOperation.CREATE, null, null, customerId,
                    contactProposal(command), command.justification());
            return new FinalCustomerContactChangeResult(null, request, mode);
        }
        FinalCustomerContact contact = persistContact(companyId, customerId, command);
        auditEventRecorder.recordDirectAction(AuditModule.CUSTOMERS, AuditAction.CREATE, "FinalCustomerContact", contact.getId(),
                null, contactAuditData(contact), metadata(customerId, "direct"));
        return new FinalCustomerContactChangeResult(contact, null, mode);
    }

    @Transactional
    public FinalCustomerContactChangeResult updateContact(UUID customerId, UUID contactId, FinalCustomerContactCommand command) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        findCustomer(customerId, companyId);
        FinalCustomerContact current = requireActiveContact(customerId, contactId, companyId);
        validateContact(command, companyId, customerId);
        ChangeAuthorizationMode mode = authorizationService.requireChangeAuthorization(AccessModule.CUSTOMERS, ChangeOperation.UPDATE);
        if (mode == ChangeAuthorizationMode.REQUEST_APPROVAL) {
            ChangeRequest request = createContactChangeRequest(ChangeRequestOperation.UPDATE, contactId, contactAuditData(current),
                    customerId, contactProposal(command), command.justification());
            return new FinalCustomerContactChangeResult(null, request, mode);
        }
        FinalCustomerContact saved = updateContact(companyId, customerId, current, command);
        auditEventRecorder.recordDirectAction(AuditModule.CUSTOMERS, AuditAction.UPDATE, "FinalCustomerContact", saved.getId(),
                contactAuditData(current), contactAuditData(saved), metadata(customerId, "direct"));
        return new FinalCustomerContactChangeResult(saved, null, mode);
    }

    @Transactional
    public FinalCustomerContactChangeResult setPrimaryContact(UUID customerId, UUID contactId) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        findCustomer(customerId, companyId);
        FinalCustomerContact current = requireActiveContact(customerId, contactId, companyId);
        ChangeAuthorizationMode mode = authorizationService.requireChangeAuthorization(AccessModule.CUSTOMERS, ChangeOperation.UPDATE);
        if (mode == ChangeAuthorizationMode.REQUEST_APPROVAL) {
            ChangeRequest request = createContactChangeRequest(ChangeRequestOperation.UPDATE, contactId, contactAuditData(current),
                    customerId, Map.of("setPrimary", true), null);
            return new FinalCustomerContactChangeResult(null, request, mode);
        }
        FinalCustomerContact saved = makePrimaryContact(companyId, customerId, current);
        auditEventRecorder.recordDirectAction(AuditModule.CUSTOMERS, AuditAction.UPDATE, "FinalCustomerContact", saved.getId(),
                contactAuditData(current), contactAuditData(saved), metadata(customerId, "direct"));
        return new FinalCustomerContactChangeResult(saved, null, mode);
    }

    @Transactional
    public FinalCustomerCancellationResult deactivateContact(UUID customerId, UUID contactId, String justification) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        findCustomer(customerId, companyId);
        FinalCustomerContact current = requireActiveContact(customerId, contactId, companyId);
        ChangeAuthorizationMode mode = authorizationService.requireChangeAuthorization(AccessModule.CUSTOMERS, ChangeOperation.CANCEL);
        if (mode == ChangeAuthorizationMode.REQUEST_APPROVAL) {
            ChangeRequest request = createContactChangeRequest(ChangeRequestOperation.CANCEL, contactId, contactAuditData(current),
                    customerId, Map.of("deactivate", true), justification);
            return new FinalCustomerCancellationResult(request, mode);
        }
        FinalCustomerContact saved = contactRepository.save(current.deactivate());
        auditEventRecorder.recordDirectAction(AuditModule.CUSTOMERS, AuditAction.DEACTIVATE, "FinalCustomerContact", saved.getId(),
                contactAuditData(current), contactAuditData(saved), metadata(customerId, "direct"));
        return new FinalCustomerCancellationResult(null, mode);
    }

    @Transactional(readOnly = true)
    public List<FinalCustomerAddress> addresses(UUID customerId) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        authorizationService.requirePermission(AccessModule.CUSTOMERS, AccessAction.READ);
        findCustomer(customerId, companyId);
        return addressRepository.findByFinalCustomerIdAndCompanyId(customerId, companyId).stream()
                .filter(FinalCustomerAddress::isActive)
                .sorted(java.util.Comparator.comparing(FinalCustomerAddress::isPrimaryAddress).reversed()
                        .thenComparing(FinalCustomerAddress::getCreatedAt, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .toList();
    }

    @Transactional
    public FinalCustomerAddressChangeResult createAddress(UUID customerId, FinalCustomerAddressCommand command) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        findCustomer(customerId, companyId);
        validateAddress(command, companyId, customerId);
        ChangeAuthorizationMode mode = authorizationService.requireChangeAuthorization(AccessModule.CUSTOMERS, ChangeOperation.CREATE);
        if (mode == ChangeAuthorizationMode.REQUEST_APPROVAL) {
            ChangeRequest request = createAddressChangeRequest(ChangeRequestOperation.CREATE, null, null, customerId,
                    addressProposal(command), command.justification());
            return new FinalCustomerAddressChangeResult(null, request, mode);
        }
        FinalCustomerAddress address = persistAddress(companyId, customerId, command);
        auditEventRecorder.recordDirectAction(AuditModule.CUSTOMERS, AuditAction.CREATE, "FinalCustomerAddress", address.getId(),
                null, addressAuditData(address), metadata(customerId, "direct"));
        return new FinalCustomerAddressChangeResult(address, null, mode);
    }

    @Transactional
    public FinalCustomerAddressChangeResult updateAddress(UUID customerId, UUID addressId, FinalCustomerAddressCommand command) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        findCustomer(customerId, companyId);
        FinalCustomerAddress current = requireActiveAddress(customerId, addressId, companyId);
        validateAddress(command, companyId, customerId);
        ChangeAuthorizationMode mode = authorizationService.requireChangeAuthorization(AccessModule.CUSTOMERS, ChangeOperation.UPDATE);
        if (mode == ChangeAuthorizationMode.REQUEST_APPROVAL) {
            ChangeRequest request = createAddressChangeRequest(ChangeRequestOperation.UPDATE, addressId, addressAuditData(current),
                    customerId, addressProposal(command), command.justification());
            return new FinalCustomerAddressChangeResult(null, request, mode);
        }
        FinalCustomerAddress saved = updateAddress(companyId, customerId, current, command);
        auditEventRecorder.recordDirectAction(AuditModule.CUSTOMERS, AuditAction.UPDATE, "FinalCustomerAddress", saved.getId(),
                addressAuditData(current), addressAuditData(saved), metadata(customerId, "direct"));
        return new FinalCustomerAddressChangeResult(saved, null, mode);
    }

    @Transactional
    public FinalCustomerAddressChangeResult setPrimaryAddress(UUID customerId, UUID addressId) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        findCustomer(customerId, companyId);
        FinalCustomerAddress current = requireActiveAddress(customerId, addressId, companyId);
        ChangeAuthorizationMode mode = authorizationService.requireChangeAuthorization(AccessModule.CUSTOMERS, ChangeOperation.UPDATE);
        if (mode == ChangeAuthorizationMode.REQUEST_APPROVAL) {
            ChangeRequest request = createAddressChangeRequest(ChangeRequestOperation.UPDATE, addressId, addressAuditData(current),
                    customerId, Map.of("setPrimary", true), null);
            return new FinalCustomerAddressChangeResult(null, request, mode);
        }
        FinalCustomerAddress saved = makePrimaryAddress(companyId, customerId, current);
        auditEventRecorder.recordDirectAction(AuditModule.CUSTOMERS, AuditAction.UPDATE, "FinalCustomerAddress", saved.getId(),
                addressAuditData(current), addressAuditData(saved), metadata(customerId, "direct"));
        return new FinalCustomerAddressChangeResult(saved, null, mode);
    }

    @Transactional
    public FinalCustomerCancellationResult deactivateAddress(UUID customerId, UUID addressId, String justification) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        findCustomer(customerId, companyId);
        FinalCustomerAddress current = requireActiveAddress(customerId, addressId, companyId);
        ChangeAuthorizationMode mode = authorizationService.requireChangeAuthorization(AccessModule.CUSTOMERS, ChangeOperation.CANCEL);
        if (mode == ChangeAuthorizationMode.REQUEST_APPROVAL) {
            ChangeRequest request = createAddressChangeRequest(ChangeRequestOperation.CANCEL, addressId, addressAuditData(current),
                    customerId, Map.of("deactivate", true), justification);
            return new FinalCustomerCancellationResult(request, mode);
        }
        FinalCustomerAddress saved = addressRepository.save(current.deactivate());
        auditEventRecorder.recordDirectAction(AuditModule.CUSTOMERS, AuditAction.DEACTIVATE, "FinalCustomerAddress", saved.getId(),
                addressAuditData(current), addressAuditData(saved), metadata(customerId, "direct"));
        return new FinalCustomerCancellationResult(null, mode);
    }

    @Transactional
    public void applyApprovedContactCreation(ChangeRequest request) {
        Map<String, Object> proposal = request.getSnapshot().proposedData().values();
        UUID customerId = proposalCustomerId(proposal);
        findCustomer(customerId, request.getCompanyId());
        FinalCustomerContact contact = persistContact(request.getCompanyId(), customerId, contactFromProposal(proposal));
        auditEventRecorder.recordDirectAction(AuditModule.CUSTOMERS, AuditAction.CREATE, "FinalCustomerContact", contact.getId(),
                null, contactAuditData(contact), metadata(customerId, "approved-change-request", request.getId()));
    }

    @Transactional
    public void applyApprovedContactUpdate(ChangeRequest request) {
        UUID customerId = proposalCustomerId(request.getSnapshot().proposedData().values());
        FinalCustomerContact current = requireActiveContact(customerId, request.getEntityId(), request.getCompanyId());
        FinalCustomerContact saved;
        if (Boolean.TRUE.equals(request.getSnapshot().proposedData().values().get("setPrimary"))) {
            saved = makePrimaryContact(request.getCompanyId(), customerId, current);
        } else {
            saved = updateContact(request.getCompanyId(), customerId, current,
                    contactFromProposal(request.getSnapshot().proposedData().values()));
        }
        auditEventRecorder.recordDirectAction(AuditModule.CUSTOMERS, AuditAction.UPDATE, "FinalCustomerContact", saved.getId(),
                contactAuditData(current), contactAuditData(saved), metadata(customerId, "approved-change-request", request.getId()));
    }

    @Transactional
    public void applyApprovedContactCancellation(ChangeRequest request) {
        UUID customerId = proposalCustomerId(request.getSnapshot().proposedData().values());
        FinalCustomerContact current = requireActiveContact(customerId, request.getEntityId(), request.getCompanyId());
        FinalCustomerContact saved = contactRepository.save(current.deactivate());
        auditEventRecorder.recordDirectAction(AuditModule.CUSTOMERS, AuditAction.DEACTIVATE, "FinalCustomerContact", saved.getId(),
                contactAuditData(current), contactAuditData(saved), metadata(customerId, "approved-change-request", request.getId()));
    }

    @Transactional
    public void applyApprovedAddressCreation(ChangeRequest request) {
        Map<String, Object> proposal = request.getSnapshot().proposedData().values();
        UUID customerId = proposalCustomerId(proposal);
        findCustomer(customerId, request.getCompanyId());
        FinalCustomerAddress address = persistAddress(request.getCompanyId(), customerId, addressFromProposal(proposal));
        auditEventRecorder.recordDirectAction(AuditModule.CUSTOMERS, AuditAction.CREATE, "FinalCustomerAddress", address.getId(),
                null, addressAuditData(address), metadata(customerId, "approved-change-request", request.getId()));
    }

    @Transactional
    public void applyApprovedAddressUpdate(ChangeRequest request) {
        UUID customerId = proposalCustomerId(request.getSnapshot().proposedData().values());
        FinalCustomerAddress current = requireActiveAddress(customerId, request.getEntityId(), request.getCompanyId());
        FinalCustomerAddress saved;
        if (Boolean.TRUE.equals(request.getSnapshot().proposedData().values().get("setPrimary"))) {
            saved = makePrimaryAddress(request.getCompanyId(), customerId, current);
        } else {
            saved = updateAddress(request.getCompanyId(), customerId, current,
                    addressFromProposal(request.getSnapshot().proposedData().values()));
        }
        auditEventRecorder.recordDirectAction(AuditModule.CUSTOMERS, AuditAction.UPDATE, "FinalCustomerAddress", saved.getId(),
                addressAuditData(current), addressAuditData(saved), metadata(customerId, "approved-change-request", request.getId()));
    }

    @Transactional
    public void applyApprovedAddressCancellation(ChangeRequest request) {
        UUID customerId = proposalCustomerId(request.getSnapshot().proposedData().values());
        FinalCustomerAddress current = requireActiveAddress(customerId, request.getEntityId(), request.getCompanyId());
        FinalCustomerAddress saved = addressRepository.save(current.deactivate());
        auditEventRecorder.recordDirectAction(AuditModule.CUSTOMERS, AuditAction.DEACTIVATE, "FinalCustomerAddress", saved.getId(),
                addressAuditData(current), addressAuditData(saved), metadata(customerId, "approved-change-request", request.getId()));
    }

    @Transactional
    public void applyApprovedCreation(ChangeRequest request) {
        persist(request.getCompanyId(), fromProposal(request.getSnapshot().proposedData().values()));
    }

    @Transactional
    public void applyApprovedUpdate(ChangeRequest request) {
        FinalCustomer current = customerRepository.findByIdAndCompanyId(request.getEntityId(), request.getCompanyId())
                .orElseThrow(() -> new TenantResourceNotFoundException("Final customer"));
        UpdateFinalCustomerCommand command = updateFromProposal(request.getSnapshot().proposedData().values());
        FinalCustomer proposed = current.updateProfile(command.customerType(), command.name(), command.document());
        ensureDocumentAvailable(request.getCompanyId(), proposed.getDocument(), current.getId());
        FinalCustomer saved = customerRepository.save(proposed);
        auditEventRecorder.recordDirectAction(AuditModule.CUSTOMERS, AuditAction.UPDATE, "FinalCustomer", saved.getId(),
                auditData(current), auditData(saved), Map.of("origin", "approved-change-request", "changeRequestId", request.getId().toString()));
    }

    private FinalCustomer persist(UUID companyId, CreateFinalCustomerCommand command) {
        FinalCustomer customer = customerRepository.save(FinalCustomer.create(companyId, command.customerType(), command.name(), command.document()));
        for (CreateFinalCustomerCommand.Contact contact : command.contacts() == null ? List.<CreateFinalCustomerCommand.Contact>of() : command.contacts()) {
            persistContact(companyId, customer.getId(), new FinalCustomerContactCommand(contact.name(), contact.email(),
                    contact.phone(), contact.primaryContact(), null));
        }
        for (CreateFinalCustomerCommand.Address address : command.addresses() == null ? List.<CreateFinalCustomerCommand.Address>of() : command.addresses()) {
            persistAddress(companyId, customer.getId(), new FinalCustomerAddressCommand(address.label(), address.postalCode(),
                    address.street(), address.number(), address.complement(), address.district(), address.city(), address.state(),
                    address.country(), address.primaryAddress(), null));
        }
        return customer;
    }

    private FinalCustomerContact persistContact(UUID companyId, UUID customerId, FinalCustomerContactCommand command) {
        if (command.primaryContact()) {
            clearOtherPrimaryContacts(companyId, customerId, null);
        }
        return contactRepository.save(FinalCustomerContact.create(companyId, customerId, command.name(), command.email(),
                command.phone(), command.primaryContact()));
    }

    private FinalCustomerContact updateContact(UUID companyId, UUID customerId, FinalCustomerContact current,
                                               FinalCustomerContactCommand command) {
        if (command.primaryContact()) {
            clearOtherPrimaryContacts(companyId, customerId, current.getId());
        }
        return contactRepository.save(current.update(command.name(), command.email(), command.phone(), command.primaryContact()));
    }

    private FinalCustomerContact makePrimaryContact(UUID companyId, UUID customerId, FinalCustomerContact current) {
        clearOtherPrimaryContacts(companyId, customerId, current.getId());
        return contactRepository.save(current.withPrimaryContact(true));
    }

    private void clearOtherPrimaryContacts(UUID companyId, UUID customerId, UUID exceptId) {
        contactRepository.findByFinalCustomerIdAndCompanyId(customerId, companyId).stream()
                .filter(FinalCustomerContact::isActive)
                .filter(FinalCustomerContact::isPrimaryContact)
                .filter(contact -> !contact.getId().equals(exceptId))
                .forEach(contact -> contactRepository.save(contact.withPrimaryContact(false)));
    }

    private FinalCustomerAddress persistAddress(UUID companyId, UUID customerId, FinalCustomerAddressCommand command) {
        if (command.primaryAddress()) {
            clearOtherPrimaryAddresses(companyId, customerId, null);
        }
        return addressRepository.save(FinalCustomerAddress.create(companyId, customerId, command.label(), command.postalCode(),
                command.street(), command.number(), command.complement(), command.district(), command.city(), command.state(),
                command.country(), command.primaryAddress()));
    }

    private FinalCustomerAddress updateAddress(UUID companyId, UUID customerId, FinalCustomerAddress current,
                                               FinalCustomerAddressCommand command) {
        if (command.primaryAddress()) {
            clearOtherPrimaryAddresses(companyId, customerId, current.getId());
        }
        return addressRepository.save(current.update(command.label(), command.postalCode(), command.street(), command.number(),
                command.complement(), command.district(), command.city(), command.state(), command.country(),
                command.primaryAddress()));
    }

    private FinalCustomerAddress makePrimaryAddress(UUID companyId, UUID customerId, FinalCustomerAddress current) {
        clearOtherPrimaryAddresses(companyId, customerId, current.getId());
        return addressRepository.save(current.withPrimaryAddress(true));
    }

    private void clearOtherPrimaryAddresses(UUID companyId, UUID customerId, UUID exceptId) {
        addressRepository.findByFinalCustomerIdAndCompanyId(customerId, companyId).stream()
                .filter(FinalCustomerAddress::isActive)
                .filter(FinalCustomerAddress::isPrimaryAddress)
                .filter(address -> !address.getId().equals(exceptId))
                .forEach(address -> addressRepository.save(address.withPrimaryAddress(false)));
    }

    private Map<String, Object> proposal(CreateFinalCustomerCommand command) {
        return Map.of("customerType", command.customerType().name(), "name", command.name(), "document", command.document(), "contacts", command.contacts() == null ? List.of() : command.contacts(), "addresses", command.addresses() == null ? List.of() : command.addresses());
    }

    private Map<String, Object> proposal(UpdateFinalCustomerCommand command) {
        return Map.of("customerType", command.customerType().name(), "name", command.name(), "document", command.document());
    }

    private ChangeRequest createContactChangeRequest(ChangeRequestOperation operation, UUID contactId,
                                                      Map<String, Object> before, UUID customerId,
                                                      Map<String, Object> proposed, String justification) {
        Map<String, Object> proposal = new java.util.HashMap<>(proposed);
        proposal.put("customerId", customerId.toString());
        return changeRequestService.create(new CreateChangeRequestCommand(AuditModule.CUSTOMERS, operation,
                "FinalCustomerContact", contactId, 0,
                new ChangeRequestSnapshot(before == null ? null : new AuditPayload(before), new AuditPayload(proposal)), justification));
    }

    private ChangeRequest createAddressChangeRequest(ChangeRequestOperation operation, UUID addressId,
                                                      Map<String, Object> before, UUID customerId,
                                                      Map<String, Object> proposed, String justification) {
        Map<String, Object> proposal = new java.util.HashMap<>(proposed);
        proposal.put("customerId", customerId.toString());
        return changeRequestService.create(new CreateChangeRequestCommand(AuditModule.CUSTOMERS, operation,
                "FinalCustomerAddress", addressId, 0,
                new ChangeRequestSnapshot(before == null ? null : new AuditPayload(before), new AuditPayload(proposal)), justification));
    }

    private Map<String, Object> contactProposal(FinalCustomerContactCommand command) {
        return Map.of("name", command.name(), "email", nullable(command.email()), "phone", nullable(command.phone()),
                "primaryContact", command.primaryContact());
    }

    private Map<String, Object> addressProposal(FinalCustomerAddressCommand command) {
        Map<String, Object> proposal = new java.util.HashMap<>();
        proposal.put("label", command.label());
        proposal.put("postalCode", command.postalCode());
        proposal.put("street", command.street());
        proposal.put("number", command.number());
        proposal.put("complement", command.complement());
        proposal.put("district", command.district());
        proposal.put("city", command.city());
        proposal.put("state", command.state());
        proposal.put("country", command.country());
        proposal.put("primaryAddress", command.primaryAddress());
        return proposal;
    }

    private FinalCustomerContactCommand contactFromProposal(Map<String, Object> proposal) {
        return new FinalCustomerContactCommand((String) proposal.get("name"), (String) proposal.get("email"),
                (String) proposal.get("phone"), Boolean.TRUE.equals(proposal.get("primaryContact")), null);
    }

    private FinalCustomerAddressCommand addressFromProposal(Map<String, Object> proposal) {
        return new FinalCustomerAddressCommand((String) proposal.get("label"), (String) proposal.get("postalCode"),
                (String) proposal.get("street"), (String) proposal.get("number"), (String) proposal.get("complement"),
                (String) proposal.get("district"), (String) proposal.get("city"), (String) proposal.get("state"),
                (String) proposal.get("country"), Boolean.TRUE.equals(proposal.get("primaryAddress")), null);
    }

    private UUID proposalCustomerId(Map<String, Object> proposal) {
        Object value = proposal.get("customerId");
        if (!(value instanceof String customerId)) {
            throw new IllegalStateException("Final customer change request must identify its customer");
        }
        return UUID.fromString(customerId);
    }

    private FinalCustomer findCustomer(UUID customerId, UUID companyId) {
        return customerRepository.findByIdAndCompanyId(customerId, companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Final customer"));
    }

    private FinalCustomerContact requireActiveContact(UUID customerId, UUID contactId, UUID companyId) {
        FinalCustomerContact contact = contactRepository.findByIdAndFinalCustomerIdAndCompanyId(contactId, customerId, companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Final customer contact"));
        if (!contact.isActive()) {
            throw new TenantResourceNotFoundException("Final customer contact");
        }
        return contact;
    }

    private FinalCustomerAddress requireActiveAddress(UUID customerId, UUID addressId, UUID companyId) {
        FinalCustomerAddress address = addressRepository.findByIdAndFinalCustomerIdAndCompanyId(addressId, customerId, companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Final customer address"));
        if (!address.isActive()) {
            throw new TenantResourceNotFoundException("Final customer address");
        }
        return address;
    }

    private Map<String, Object> contactAuditData(FinalCustomerContact contact) {
        return Map.of("active", contact.isActive(), "primaryContact", contact.isPrimaryContact());
    }

    private Map<String, Object> addressAuditData(FinalCustomerAddress address) {
        return Map.of("active", address.isActive(), "primaryAddress", address.isPrimaryAddress());
    }

    private Map<String, Object> metadata(UUID customerId, String origin) {
        return Map.of("customerId", customerId.toString(), "origin", origin);
    }

    private Map<String, Object> metadata(UUID customerId, String origin, UUID changeRequestId) {
        return Map.of("customerId", customerId.toString(), "origin", origin, "changeRequestId", changeRequestId.toString());
    }

    private String nullable(String value) {
        return value == null ? "" : value;
    }

    private void validateContact(FinalCustomerContactCommand command, UUID companyId, UUID customerId) {
        FinalCustomerContact.create(companyId, customerId, command.name(), command.email(), command.phone(),
                command.primaryContact());
    }

    private void validateAddress(FinalCustomerAddressCommand command, UUID companyId, UUID customerId) {
        FinalCustomerAddress.create(companyId, customerId, command.label(), command.postalCode(), command.street(),
                command.number(), command.complement(), command.district(), command.city(), command.state(), command.country(),
                command.primaryAddress());
    }

    @SuppressWarnings("unchecked")
    private CreateFinalCustomerCommand fromProposal(Map<String, Object> data) {
        var type = com.renovar.canteiro.io.customers.domain.FinalCustomerType.valueOf((String) data.get("customerType"));
        List<CreateFinalCustomerCommand.Contact> contacts = ((List<Map<String, Object>>) data.getOrDefault("contacts", List.of())).stream().map(c -> new CreateFinalCustomerCommand.Contact((String) c.get("name"), (String) c.get("email"), (String) c.get("phone"), Boolean.TRUE.equals(c.get("primaryContact")))).toList();
        List<CreateFinalCustomerCommand.Address> addresses = ((List<Map<String, Object>>) data.getOrDefault("addresses", List.of())).stream().map(a -> new CreateFinalCustomerCommand.Address((String) a.get("label"), (String) a.get("postalCode"), (String) a.get("street"), (String) a.get("number"), (String) a.get("complement"), (String) a.get("district"), (String) a.get("city"), (String) a.get("state"), (String) a.get("country"), Boolean.TRUE.equals(a.get("primaryAddress")))).toList();
        return new CreateFinalCustomerCommand(type, (String) data.get("name"), (String) data.get("document"), contacts, addresses, null);
    }

    private UpdateFinalCustomerCommand updateFromProposal(Map<String, Object> data) {
        return new UpdateFinalCustomerCommand(
                com.renovar.canteiro.io.customers.domain.FinalCustomerType.valueOf((String) data.get("customerType")),
                (String) data.get("name"),
                (String) data.get("document"),
                null
        );
    }

    private void ensureDocumentAvailable(UUID companyId, String document, UUID currentCustomerId) {
        customerRepository.findByCompanyIdAndDocument(companyId, document)
                .filter(existing -> !existing.getId().equals(currentCustomerId))
                .ifPresent(existing -> {
                    throw new ApiException(HttpStatus.CONFLICT, ErrorCode.BUSINESS_RULE_VIOLATION,
                            "Final customer document is already in use");
                });
    }

    private Map<String, Object> auditData(FinalCustomer customer) { return Map.of("name", customer.getName(), "customerType", customer.getCustomerType().name(), "active", customer.isActive()); }
}
