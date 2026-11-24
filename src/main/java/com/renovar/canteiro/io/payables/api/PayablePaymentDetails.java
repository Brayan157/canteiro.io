package com.renovar.canteiro.io.payables.api;

import com.renovar.canteiro.io.payables.domain.PayablePaymentContent;
import com.renovar.canteiro.io.payables.domain.PayablePaymentInstructionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description="Company-provided instructions for manual payment outside this system. No beneficiary verification, issuance, payment execution or settlement. Mutually exclusive fields by type. BOLETO may initially have no code to allow a later private PDF upload; without code/PDF it has no usable payment reference.")
public record PayablePaymentDetails(@NotNull PayablePaymentInstructionType type,
        @Size(max=48) @Schema(types={"string","null"},pattern="[0-9]{47,48}",description="BOLETO only. Digits, 47 or 48; cannot coexist with barcode. No bank verification.") String digitableLine,
        @Size(max=44) @Schema(types={"string","null"},pattern="[0-9]{44}",description="BOLETO only. 44 digits; cannot coexist with digitableLine.") String barcode,
        @Schema(allowableValues={"CPF","CNPJ","EMAIL","PHONE","RANDOM"},description="Required only with pixKey.") String pixKeyType,
        @Size(max=254) @Schema(types={"string","null"},description="PIX only. Key as supplied by user, not resolved at the bank. CPF/CNPJ digits; PHONE E.164; RANDOM UUID; EMAIL email.") String pixKey,
        @Size(max=1024) @Schema(types={"string","null"},description="PIX only. User-provided copy/paste text, mutually exclusive with pixKey/pixKeyType. Stored verbatim after outer whitespace normalization, never generated or claimed as bank-validated.") String pixCopyPaste,
        @Schema(types={"string","null"}) @Size(max=8) String bankCode,@Schema(types={"string","null"}) @Size(max=120) String bankName,
        @Schema(types={"string","null"}) @Size(max=20) String branch,@Schema(types={"string","null"}) @Size(max=5) String branchCheckDigit,
        @Schema(types={"string","null"}) @Size(max=30) String accountNumber,@Schema(types={"string","null"}) @Size(max=5) String accountCheckDigit,
        @Schema(allowableValues={"CHECKING","SAVINGS","PAYMENT"},description="BANK_TRANSFER only; bank code or name, branch, account number, type and holder name are required. Check digits are optional because formats differ.") String accountType,
        @Schema(types={"string","null"}) @Size(max=160) String holderName) {
    public PayablePaymentContent content(){return new PayablePaymentContent(type,digitableLine,barcode,pixKeyType,pixKey,pixCopyPaste,
            bankCode,bankName,branch,branchCheckDigit,accountNumber,accountCheckDigit,accountType,holderName);}
    public static PayablePaymentDetails from(PayablePaymentContent p){return new PayablePaymentDetails(p.type(),p.digitableLine(),p.barcode(),p.pixKeyType(),p.pixKey(),p.pixCopyPaste(),
            p.bankCode(),p.bankName(),p.branch(),p.branchCheckDigit(),p.accountNumber(),p.accountCheckDigit(),p.accountType(),p.holderName());}
    @Override public String toString(){return "PayablePaymentDetails[redacted]";}
}
