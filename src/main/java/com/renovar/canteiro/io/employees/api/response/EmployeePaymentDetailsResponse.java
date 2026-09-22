package com.renovar.canteiro.io.employees.api.response;

import com.renovar.canteiro.io.employees.domain.EmployeeBankAccountType;
import com.renovar.canteiro.io.employees.domain.EmployeePixKeyType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(name = "EmployeePaymentDetailsResponse", description = "Complete payment reference, returned only after an explicit, audited request authorized by EMPLOYEES.VIEW_PAYMENT_DETAILS. Do not cache, export or log these values.")
public record EmployeePaymentDetailsResponse(
        UUID id,
        String bankName,
        String bankCode,
        String branch,
        EmployeeBankAccountType accountType,
        @Schema(description = "Complete bank account number. Sensitive; display only during the manual-payment interaction.") String accountNumber,
        @Schema(description = "Complete account check digit. Sensitive; display only during the manual-payment interaction.") String accountCheckDigit,
        EmployeePixKeyType pixKeyType,
        @Schema(description = "Complete Pix key. Sensitive; display only during the manual-payment interaction.") String pixKey,
        @Schema(description = "Static BR Code Pix copy-and-paste payload, with CRC. It has no amount, charge, due date or financial identifier. Null for legacy Pix references until the confirmed holder name and city are provided.") String pixCopyPaste,
        @Schema(description = "PNG QR Code as a data URL for the static Pix payload. It has no amount, charge, due date or financial identifier. Null when pixCopyPaste is null.") String pixQrCode
) { }
