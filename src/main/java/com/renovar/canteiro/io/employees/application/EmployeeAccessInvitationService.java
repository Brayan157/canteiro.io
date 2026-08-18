package com.renovar.canteiro.io.employees.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.employees.domain.Employee;
import com.renovar.canteiro.io.employees.domain.EmployeeRepository;
import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.identity.application.AccountActivationEmailSender;
import com.renovar.canteiro.io.identity.application.AccountActivationProperties;
import com.renovar.canteiro.io.identity.application.ActivationTokenGenerator;
import com.renovar.canteiro.io.identity.application.ActivationTokenHasher;
import com.renovar.canteiro.io.identity.domain.AccountActivationToken;
import com.renovar.canteiro.io.identity.domain.AccountActivationTokenRepository;
import com.renovar.canteiro.io.identity.domain.CompanyUser;
import com.renovar.canteiro.io.identity.domain.CompanyUserRepository;
import com.renovar.canteiro.io.identity.domain.User;
import com.renovar.canteiro.io.identity.domain.UserRepository;
import com.renovar.canteiro.io.identity.domain.UserType;
import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmployeeAccessInvitationService {

    private final TenantContextHolder tenantContextHolder;
    private final AccessAuthorizationService authorizationService;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final CompanyUserRepository companyUserRepository;
    private final AccountActivationTokenRepository activationTokenRepository;
    private final ActivationTokenGenerator activationTokenGenerator;
    private final ActivationTokenHasher activationTokenHasher;
    private final AccountActivationEmailSender activationEmailSender;
    private final AccountActivationProperties activationProperties;
    private final AuditEventRecorder auditEventRecorder;
    private final Clock clock;

    @Transactional
    public EmployeeAccessInvitationResult invite(InviteEmployeeAccessCommand command) {
        authorizationService.requirePermission(AccessModule.USERS, AccessAction.MANAGE_USERS);
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        Employee employee = employeeRepository.findByIdAndCompanyId(command.employeeId(), companyId)
                .orElseThrow(() -> new com.renovar.canteiro.io.access.application.TenantResourceNotFoundException("Employee"));
        String email = command.email().trim();
        if (userRepository.findByEmail(email).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.BUSINESS_RULE_VIOLATION, "Email is already in use");
        }

        User user = userRepository.save(User.create(email, UserType.COMPANY));
        companyUserRepository.save(CompanyUser.create(user.getId(), companyId));
        employee.linkUser(user.getId());
        employeeRepository.save(employee);
        sendActivation(user);

        auditEventRecorder.recordDirectAction(AuditModule.USERS, AuditAction.CREATE, "User", user.getId(), null,
                Map.of("email", user.getEmail(), "employeeId", employee.getId().toString()),
                Map.of("origin", "employee-access-invitation"));
        auditEventRecorder.recordDirectAction(AuditModule.EMPLOYEES, AuditAction.UPDATE, "Employee", employee.getId(),
                Map.of("hasSystemAccess", false), Map.of("hasSystemAccess", true, "userId", user.getId().toString()),
                Map.of("origin", "employee-access-invitation"));
        return new EmployeeAccessInvitationResult(employee.getId(), user.getId(), user.getEmail());
    }

    private void sendActivation(User user) {
        String rawToken = activationTokenGenerator.generate();
        Instant expiresAt = clock.instant().plus(activationProperties.tokenTtl());
        activationTokenRepository.save(AccountActivationToken.create(user.getId(), activationTokenHasher.hash(rawToken), expiresAt));
        activationEmailSender.send(user.getEmail(), rawToken, expiresAt);
    }
}
