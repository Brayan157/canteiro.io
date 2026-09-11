package com.renovar.canteiro.io.employees.infrastructure.persistence;

import com.renovar.canteiro.io.employees.domain.EmployeeBankAccountType;
import com.renovar.canteiro.io.employees.domain.EmployeePixKeyType;
import com.renovar.canteiro.io.shared.infrastructure.persistence.jpa.BaseJpaEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Getter @Entity @Table(name = "employee_payment_account") @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmployeePaymentAccountJpaEntity extends BaseJpaEntity {
    @Column(name="company_id", nullable=false) private UUID companyId;
    @Column(name="employee_id", nullable=false) private UUID employeeId;
    @Column(name="bank_name") private String bankName;
    @Column(name="bank_code") private String bankCode;
    @Column(name="branch") private String branch;
    @Enumerated(EnumType.STRING) @Column(name="account_type") private EmployeeBankAccountType accountType;
    @Column(name="account_number_ciphertext") private String accountNumberCiphertext;
    @Column(name="account_number_masked") private String accountNumberMasked;
    @Column(name="account_check_digit_ciphertext") private String accountCheckDigitCiphertext;
    @Enumerated(EnumType.STRING) @Column(name="pix_key_type") private EmployeePixKeyType pixKeyType;
    @Column(name="pix_key_ciphertext") private String pixKeyCiphertext;
    @Column(name="pix_key_masked") private String pixKeyMasked;
    @Column(name="pix_receiver_name_ciphertext") private String pixReceiverNameCiphertext;
    @Column(name="pix_receiver_city_ciphertext") private String pixReceiverCityCiphertext;
    @Column(nullable=false) private boolean active;
    public EmployeePaymentAccountJpaEntity(UUID companyId, UUID employeeId, String bankName, String bankCode, String branch,
            EmployeeBankAccountType accountType, String accountNumberCiphertext, String accountNumberMasked,
            String accountCheckDigitCiphertext,
            EmployeePixKeyType pixKeyType, String pixKeyCiphertext, String pixKeyMasked,
            String pixReceiverNameCiphertext, String pixReceiverCityCiphertext) {
        this.companyId=companyId; this.employeeId=employeeId; this.bankName=bankName; this.bankCode=bankCode; this.branch=branch;
        this.accountType=accountType; this.accountNumberCiphertext=accountNumberCiphertext; this.accountNumberMasked=accountNumberMasked;
        this.accountCheckDigitCiphertext=accountCheckDigitCiphertext;
        this.pixKeyType=pixKeyType; this.pixKeyCiphertext=pixKeyCiphertext; this.pixKeyMasked=pixKeyMasked;
        this.pixReceiverNameCiphertext=pixReceiverNameCiphertext; this.pixReceiverCityCiphertext=pixReceiverCityCiphertext;
        this.active=true;
    }
    public void update(String bankName, String bankCode, String branch, EmployeeBankAccountType accountType,
                       String accountNumberCiphertext, String accountNumberMasked, String accountCheckDigitCiphertext,
                       EmployeePixKeyType pixKeyType, String pixKeyCiphertext, String pixKeyMasked,
                       String pixReceiverNameCiphertext, String pixReceiverCityCiphertext) {
        this.bankName=bankName; this.bankCode=bankCode; this.branch=branch; this.accountType=accountType;
        this.accountNumberCiphertext=accountNumberCiphertext; this.accountNumberMasked=accountNumberMasked;
        this.accountCheckDigitCiphertext=accountCheckDigitCiphertext;
        this.pixKeyType=pixKeyType; this.pixKeyCiphertext=pixKeyCiphertext; this.pixKeyMasked=pixKeyMasked;
        this.pixReceiverNameCiphertext=pixReceiverNameCiphertext; this.pixReceiverCityCiphertext=pixReceiverCityCiphertext;
    }
    public void deactivate() { this.active=false; }
}
