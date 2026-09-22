package com.renovar.canteiro.io.employees.api.response;

import com.renovar.canteiro.io.employees.domain.EmployeeBankAccountType;
import com.renovar.canteiro.io.employees.domain.EmployeePixKeyType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(name = "EmployeePaymentAccountResponse", description = "Encrypted employee bank or Pix reference. Complete account and Pix values are never returned.")
public record EmployeePaymentAccountResponse(
        UUID id,
        String bankName,
        String bankCode,
        String branch,
        EmployeeBankAccountType accountType,
        @Schema(description = "Masked bank account number only", example = "***1234") String accountNumberMasked,
        EmployeePixKeyType pixKeyType,
        @Schema(description = "Masked Pix key only", example = "***5678") String pixKeyMasked,
        boolean active
) { }
