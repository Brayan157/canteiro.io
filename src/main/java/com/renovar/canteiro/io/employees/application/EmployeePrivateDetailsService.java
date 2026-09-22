package com.renovar.canteiro.io.employees.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.employees.api.request.EmployeeAddressRequest;
import com.renovar.canteiro.io.employees.api.request.EmployeePaymentAccountRequest;
import com.renovar.canteiro.io.employees.api.response.EmployeeAddressResponse;
import com.renovar.canteiro.io.employees.api.response.EmployeePaymentAccountResponse;
import com.renovar.canteiro.io.employees.api.response.EmployeePaymentDetailsResponse;
import com.renovar.canteiro.io.employees.domain.EmployeeBankAccountType;
import com.renovar.canteiro.io.employees.domain.EmployeePixKeyType;
import com.renovar.canteiro.io.employees.infrastructure.persistence.EmployeeAddressJpaEntity;
import com.renovar.canteiro.io.employees.infrastructure.persistence.EmployeeAddressJpaRepository;
import com.renovar.canteiro.io.employees.infrastructure.persistence.EmployeePaymentAccountJpaEntity;
import com.renovar.canteiro.io.employees.infrastructure.persistence.EmployeePaymentAccountJpaRepository;
import com.renovar.canteiro.io.employees.infrastructure.security.EmployeePaymentDataCipher;
import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmployeePrivateDetailsService {

    private final TenantContextHolder tenantContextHolder;
    private final AccessAuthorizationService authorizationService;
    private final EmployeeManagementService employeeManagementService;
    private final EmployeeAddressJpaRepository addressRepository;
    private final EmployeePaymentAccountJpaRepository paymentAccountRepository;
    private final EmployeePaymentDataCipher paymentDataCipher;
    private final PixStaticQrCodeGenerator pixStaticQrCodeGenerator;
    private final AuditEventRecorder auditEventRecorder;

    @Transactional
    public EmployeeAddressResponse addAddress(UUID employeeId, EmployeeAddressRequest request) {
        authorizationService.requirePermission(AccessModule.EMPLOYEES, AccessAction.CREATE_DIRECT);
        UUID companyId = companyId(employeeId);
        EmployeeAddressJpaEntity address = addressRepository.save(new EmployeeAddressJpaEntity(
                companyId, employeeId, trimToNull(request.label()), trimToNull(request.postalCode()),
                request.street().trim(), trimToNull(request.number()), trimToNull(request.complement()),
                trimToNull(request.district()), request.city().trim(), trimToNull(request.state()),
                request.country().trim(), request.primaryAddress()));
        auditEventRecorder.recordDirectAction(AuditModule.EMPLOYEES, AuditAction.CREATE, "EmployeeAddress", address.getId(),
                null, addressAuditData(address), Map.of("employeeId", employeeId.toString()));
        return address(address);
    }

    @Transactional
    public EmployeeAddressResponse updateAddress(UUID employeeId, UUID addressId, EmployeeAddressRequest request) {
        authorizationService.requirePermission(AccessModule.EMPLOYEES, AccessAction.UPDATE_DIRECT);
        UUID companyId = companyId(employeeId);
        EmployeeAddressJpaEntity address = address(companyId, employeeId, addressId);
        Map<String, Object> before = addressAuditData(address);
        address.update(trimToNull(request.label()), trimToNull(request.postalCode()), request.street().trim(),
                trimToNull(request.number()), trimToNull(request.complement()), trimToNull(request.district()),
                request.city().trim(), trimToNull(request.state()), request.country().trim(), request.primaryAddress());
        auditEventRecorder.recordDirectAction(AuditModule.EMPLOYEES, AuditAction.UPDATE, "EmployeeAddress", address.getId(),
                before, addressAuditData(address), Map.of("employeeId", employeeId.toString()));
        return address(address);
    }

    @Transactional
    public void deactivateAddress(UUID employeeId, UUID addressId) {
        authorizationService.requirePermission(AccessModule.EMPLOYEES, AccessAction.CANCEL_DIRECT);
        UUID companyId = companyId(employeeId);
        EmployeeAddressJpaEntity address = address(companyId, employeeId, addressId);
        Map<String, Object> before = addressAuditData(address);
        address.deactivate();
        auditEventRecorder.recordDirectAction(AuditModule.EMPLOYEES, AuditAction.DEACTIVATE, "EmployeeAddress", address.getId(),
                before, addressAuditData(address), Map.of("employeeId", employeeId.toString()));
    }

    @Transactional
    public EmployeeAddressResponse setPrimaryAddress(UUID employeeId, UUID addressId) {
        authorizationService.requirePermission(AccessModule.EMPLOYEES, AccessAction.UPDATE_DIRECT);
        UUID companyId = companyId(employeeId);
        EmployeeAddressJpaEntity address = address(companyId, employeeId, addressId);
        addressRepository.findByCompanyIdAndEmployeeIdAndActiveTrueAndPrimaryAddressTrue(companyId, employeeId)
                .forEach(currentPrimary -> currentPrimary.setPrimaryAddress(false));
        address.setPrimaryAddress(true);
        auditEventRecorder.recordDirectAction(AuditModule.EMPLOYEES, AuditAction.UPDATE, "EmployeeAddress", address.getId(),
                null, Map.of("primaryAddress", true), Map.of("employeeId", employeeId.toString()));
        return address(address);
    }

    @Transactional(readOnly = true)
    public List<EmployeeAddressResponse> addresses(UUID employeeId) {
        authorizationService.requirePermission(AccessModule.EMPLOYEES, AccessAction.READ);
        UUID companyId = companyId(employeeId);
        return addressRepository.findByCompanyIdAndEmployeeIdAndActiveTrueOrderByPrimaryAddressDescCreatedAtAsc(companyId, employeeId)
                .stream().map(this::address).toList();
    }

    @Transactional
    public EmployeePaymentAccountResponse addPaymentAccount(UUID employeeId, EmployeePaymentAccountRequest request) {
        authorizationService.requirePermission(AccessModule.EMPLOYEES, AccessAction.CREATE_DIRECT);
        validateNewPaymentDetails(request);
        UUID companyId = companyId(employeeId);
        EmployeePaymentAccountJpaEntity account = paymentAccountRepository.save(new EmployeePaymentAccountJpaEntity(
                companyId, employeeId, trimToNull(request.bankName()), trimToNull(request.bankCode()), trimToNull(request.branch()),
                request.accountType(), paymentDataCipher.encrypt(request.accountNumber()), mask(request.accountNumber()),
                paymentDataCipher.encrypt(request.accountCheckDigit()),
                request.pixKeyType(), paymentDataCipher.encrypt(request.pixKey()), mask(request.pixKey()),
                paymentDataCipher.encrypt(request.pixReceiverName()), paymentDataCipher.encrypt(request.pixReceiverCity())));
        auditEventRecorder.recordDirectAction(AuditModule.EMPLOYEES, AuditAction.CREATE, "EmployeePaymentAccount", account.getId(),
                null, paymentAuditData(account), Map.of("employeeId", employeeId.toString()));
        return payment(account);
    }

    @Transactional
    public EmployeePaymentAccountResponse updatePaymentAccount(UUID employeeId, UUID accountId,
                                                                 EmployeePaymentAccountRequest request) {
        authorizationService.requirePermission(AccessModule.EMPLOYEES, AccessAction.UPDATE_DIRECT);
        UUID companyId = companyId(employeeId);
        EmployeePaymentAccountJpaEntity account = paymentAccount(companyId, employeeId, accountId);
        Map<String, Object> before = paymentAuditData(account);

        boolean replacingAccountNumber = !blank(request.accountNumber());
        boolean replacingAccountCheckDigit = !blank(request.accountCheckDigit());
        boolean replacingPixKey = !blank(request.pixKey());
        boolean replacingPixReceiver = !blank(request.pixReceiverName()) || !blank(request.pixReceiverCity());
        if (replacingPixReceiver && !replacingPixKey && account.getPixKeyCiphertext() == null) {
            throw new IllegalArgumentException("Pix receiver details require a Pix key");
        }
        if (replacingAccountCheckDigit && !replacingAccountNumber && account.getAccountNumberCiphertext() == null) {
            throw new IllegalArgumentException("An account check digit requires a bank account");
        }
        EmployeeBankAccountType accountType = replacingAccountNumber
                ? required(request.accountType(), "Bank account type is required")
                : request.accountType() == null ? account.getAccountType() : request.accountType();
        EmployeePixKeyType pixKeyType = replacingPixKey
                ? required(request.pixKeyType(), "Pix key type is required")
                : request.pixKeyType() == null ? account.getPixKeyType() : request.pixKeyType();
        if (replacingPixKey || replacingPixReceiver) {
            requirePixReceiver(request);
            pixStaticQrCodeGenerator.validate(replacingPixKey ? request.pixKey()
                    : paymentDataCipher.decrypt(account.getPixKeyCiphertext()), request.pixReceiverName(), request.pixReceiverCity());
        }
        String accountCiphertext = replacingAccountNumber
                ? paymentDataCipher.encrypt(request.accountNumber()) : account.getAccountNumberCiphertext();
        String accountMasked = replacingAccountNumber ? mask(request.accountNumber()) : account.getAccountNumberMasked();
        String accountCheckDigitCiphertext = replacingAccountNumber
                ? paymentDataCipher.encrypt(request.accountCheckDigit())
                : replacingAccountCheckDigit ? paymentDataCipher.encrypt(request.accountCheckDigit())
                : account.getAccountCheckDigitCiphertext();
        String pixCiphertext = replacingPixKey ? paymentDataCipher.encrypt(request.pixKey()) : account.getPixKeyCiphertext();
        String pixMasked = replacingPixKey ? mask(request.pixKey()) : account.getPixKeyMasked();
        String pixReceiverNameCiphertext = replacingPixKey || replacingPixReceiver
                ? paymentDataCipher.encrypt(request.pixReceiverName()) : account.getPixReceiverNameCiphertext();
        String pixReceiverCityCiphertext = replacingPixKey || replacingPixReceiver
                ? paymentDataCipher.encrypt(request.pixReceiverCity()) : account.getPixReceiverCityCiphertext();
        validateStoredPaymentDetails(accountType, accountCiphertext, pixKeyType, pixCiphertext);

        account.update(trimToNull(request.bankName()), trimToNull(request.bankCode()), trimToNull(request.branch()),
                accountType, accountCiphertext, accountMasked, accountCheckDigitCiphertext,
                pixKeyType, pixCiphertext, pixMasked, pixReceiverNameCiphertext, pixReceiverCityCiphertext);
        auditEventRecorder.recordDirectAction(AuditModule.EMPLOYEES, AuditAction.UPDATE, "EmployeePaymentAccount", account.getId(),
                before, paymentAuditData(account), Map.of("employeeId", employeeId.toString()));
        return payment(account);
    }

    @Transactional
    public void deactivatePaymentAccount(UUID employeeId, UUID accountId) {
        authorizationService.requirePermission(AccessModule.EMPLOYEES, AccessAction.CANCEL_DIRECT);
        UUID companyId = companyId(employeeId);
        EmployeePaymentAccountJpaEntity account = paymentAccount(companyId, employeeId, accountId);
        Map<String, Object> before = paymentAuditData(account);
        account.deactivate();
        auditEventRecorder.recordDirectAction(AuditModule.EMPLOYEES, AuditAction.DEACTIVATE, "EmployeePaymentAccount", account.getId(),
                before, paymentAuditData(account), Map.of("employeeId", employeeId.toString()));
    }

    @Transactional(readOnly = true)
    public List<EmployeePaymentAccountResponse> paymentAccounts(UUID employeeId) {
        authorizationService.requirePermission(AccessModule.EMPLOYEES, AccessAction.READ);
        UUID companyId = companyId(employeeId);
        return paymentAccountRepository.findByCompanyIdAndEmployeeIdAndActiveTrueOrderByCreatedAtAsc(companyId, employeeId)
                .stream().map(this::payment).toList();
    }

    @Transactional
    public EmployeePaymentDetailsResponse revealPaymentDetails(UUID employeeId, UUID accountId) {
        authorizationService.requirePermission(AccessModule.EMPLOYEES, AccessAction.VIEW_PAYMENT_DETAILS);
        UUID companyId = companyId(employeeId);
        EmployeePaymentAccountJpaEntity account = paymentAccount(companyId, employeeId, accountId);
        if (!account.isActive()) {
            throw new TenantResourceNotFoundException("Employee payment account");
        }
        auditEventRecorder.recordDirectAction(AuditModule.EMPLOYEES, AuditAction.VIEW_SENSITIVE,
                "EmployeePaymentAccount", account.getId(), null, null,
                Map.of("employeeId", employeeId.toString(), "origin", "direct", "purpose", "manual-payment"));
        String pixKey = paymentDataCipher.decrypt(account.getPixKeyCiphertext());
        PixStaticQrCodeGenerator.PixStaticQrCode pixQrCode = pixKey == null
                || account.getPixReceiverNameCiphertext() == null || account.getPixReceiverCityCiphertext() == null
                ? null : pixStaticQrCodeGenerator.generate(pixKey,
                paymentDataCipher.decrypt(account.getPixReceiverNameCiphertext()),
                paymentDataCipher.decrypt(account.getPixReceiverCityCiphertext()));
        return new EmployeePaymentDetailsResponse(account.getId(), account.getBankName(), account.getBankCode(),
                account.getBranch(), account.getAccountType(), paymentDataCipher.decrypt(account.getAccountNumberCiphertext()),
                paymentDataCipher.decrypt(account.getAccountCheckDigitCiphertext()), account.getPixKeyType(), pixKey,
                pixQrCode == null ? null : pixQrCode.copyPaste(), pixQrCode == null ? null : pixQrCode.qrCodeDataUrl());
    }

    private UUID companyId(UUID employeeId) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        employeeManagementService.findInCurrentCompany(employeeId);
        return companyId;
    }

    private EmployeeAddressJpaEntity address(UUID companyId, UUID employeeId, UUID addressId) {
        return addressRepository.findByIdAndCompanyIdAndEmployeeId(addressId, companyId, employeeId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Employee address"));
    }

    private EmployeePaymentAccountJpaEntity paymentAccount(UUID companyId, UUID employeeId, UUID accountId) {
        return paymentAccountRepository.findByIdAndCompanyIdAndEmployeeId(accountId, companyId, employeeId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Employee payment account"));
    }

    private void validateNewPaymentDetails(EmployeePaymentAccountRequest request) {
        if (blank(request.accountNumber()) && blank(request.pixKey())) {
            throw new IllegalArgumentException("A bank account or Pix key is required");
        }
        if (!blank(request.accountNumber()) && request.accountType() == null) {
            throw new IllegalArgumentException("Bank account type is required");
        }
        if (!blank(request.accountCheckDigit()) && blank(request.accountNumber())) {
            throw new IllegalArgumentException("An account check digit requires a bank account");
        }
        if (!blank(request.pixKey()) && request.pixKeyType() == null) {
            throw new IllegalArgumentException("Pix key type is required");
        }
        if (!blank(request.pixKey())) {
            requirePixReceiver(request);
            pixStaticQrCodeGenerator.validate(request.pixKey(), request.pixReceiverName(), request.pixReceiverCity());
        }
        if (blank(request.pixKey()) && (!blank(request.pixReceiverName()) || !blank(request.pixReceiverCity()))) {
            throw new IllegalArgumentException("Pix receiver details require a Pix key");
        }
    }

    private void validateStoredPaymentDetails(EmployeeBankAccountType accountType, String accountCiphertext,
                                              EmployeePixKeyType pixKeyType, String pixCiphertext) {
        if (accountCiphertext == null && pixCiphertext == null) {
            throw new IllegalArgumentException("A bank account or Pix key is required");
        }
        if (accountCiphertext != null && accountType == null) {
            throw new IllegalArgumentException("Bank account type is required");
        }
        if (pixCiphertext != null && pixKeyType == null) {
            throw new IllegalArgumentException("Pix key type is required");
        }
    }

    private EmployeeAddressResponse address(EmployeeAddressJpaEntity entity) {
        return new EmployeeAddressResponse(entity.getId(), entity.getLabel(), entity.getPostalCode(), entity.getStreet(),
                entity.getNumber(), entity.getComplement(), entity.getDistrict(), entity.getCity(), entity.getState(),
                entity.getCountry(), entity.isPrimaryAddress(), entity.isActive());
    }

    private EmployeePaymentAccountResponse payment(EmployeePaymentAccountJpaEntity entity) {
        return new EmployeePaymentAccountResponse(entity.getId(), entity.getBankName(), entity.getBankCode(), entity.getBranch(),
                entity.getAccountType(), entity.getAccountNumberMasked(), entity.getPixKeyType(), entity.getPixKeyMasked(), entity.isActive());
    }

    private Map<String, Object> addressAuditData(EmployeeAddressJpaEntity entity) {
        return Map.of("active", entity.isActive(), "primaryAddress", entity.isPrimaryAddress());
    }

    private Map<String, Object> paymentAuditData(EmployeePaymentAccountJpaEntity entity) {
        return Map.of("active", entity.isActive(), "hasBankAccount", entity.getAccountNumberCiphertext() != null,
                "hasPixKey", entity.getPixKeyCiphertext() != null,
                "hasConfirmedPixReceiver", entity.getPixReceiverNameCiphertext() != null
                        && entity.getPixReceiverCityCiphertext() != null);
    }

    private void requirePixReceiver(EmployeePaymentAccountRequest request) {
        if (blank(request.pixReceiverName()) || blank(request.pixReceiverCity())) {
            throw new IllegalArgumentException("Confirmed Pix receiver name and city are required");
        }
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }

    private String trimToNull(String value) { return blank(value) ? null : value.trim(); }

    private String mask(String value) {
        if (blank(value)) return null;
        String clean = value.trim();
        return "***" + clean.substring(Math.max(0, clean.length() - 4));
    }

    private <T> T required(T value, String message) {
        if (value == null) throw new IllegalArgumentException(message);
        return value;
    }
}
