package com.renovar.canteiro.io.employees.api.request;

import com.renovar.canteiro.io.employees.domain.EmployeeBankAccountType;
import com.renovar.canteiro.io.employees.domain.EmployeePixKeyType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EmployeePaymentAccountRequest(
        @Size(max = 150) String bankName, @Size(max = 20) String bankCode, @Size(max = 30) String branch,
        EmployeeBankAccountType accountType,
        @Size(max = 80) @Schema(writeOnly = true, description = "Complete account number. Stored encrypted and never returned.") String accountNumber,
        @Pattern(regexp = "^[A-Za-z0-9]{1,2}$", message = "Account check digit must have one or two letters or numbers")
        @Schema(writeOnly = true, description = "Optional account check digit. Stored encrypted and only revealed through the controlled payment-details action.") String accountCheckDigit,
        EmployeePixKeyType pixKeyType,
        @Size(max = 255) @Schema(writeOnly = true, description = "Complete Pix key. Stored encrypted and only revealed through the controlled payment-details action.") String pixKey,
        @Size(max = 120) @Schema(writeOnly = true, description = "Confirmed name of the Pix key holder. Required with a Pix key for a static BR Code Pix QR; never inferred from the employee profile.") String pixReceiverName,
        @Size(max = 80) @Schema(writeOnly = true, description = "Confirmed city of the Pix key holder. Required with a Pix key for a static BR Code Pix QR; never inferred from the employee address.") String pixReceiverCity
) { }
