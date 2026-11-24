package com.renovar.canteiro.io.payables.domain;

import java.util.Set;

/** Instructions provided by the company, never a payment order or verified beneficiary. */
public record PayablePaymentContent(PayablePaymentInstructionType type, String digitableLine, String barcode,
        String pixKeyType, String pixKey, String pixCopyPaste, String bankCode, String bankName,
        String branch, String branchCheckDigit, String accountNumber, String accountCheckDigit,
        String accountType, String holderName) {
    public PayablePaymentContent {
        if(type==null)throw new IllegalArgumentException("Payment instruction type is required");
        digitableLine=text(digitableLine,48);barcode=text(barcode,44);
        pixKeyType=text(pixKeyType,20);pixKey=text(pixKey,254);pixCopyPaste=text(pixCopyPaste,1024);
        bankCode=text(bankCode,8);bankName=text(bankName,120);branch=text(branch,20);branchCheckDigit=text(branchCheckDigit,5);
        accountNumber=text(accountNumber,30);accountCheckDigit=text(accountCheckDigit,5);
        accountType=text(accountType,20);holderName=text(holderName,160);
        switch(type){
            case BOLETO -> {
                if(digitableLine!=null&&barcode!=null)invalid("Provide at most one of digitableLine or barcode");
                if(digitableLine!=null&&!digitableLine.matches("[0-9]{47,48}"))invalid("digitableLine must contain 47 or 48 digits");
                if(barcode!=null&&!barcode.matches("[0-9]{44}"))invalid("barcode must contain 44 digits");
                absent(pixKeyType,pixKey,pixCopyPaste,bankCode,bankName,branch,branchCheckDigit,accountNumber,accountCheckDigit,accountType,holderName);
            }
            case PIX -> {
                if((pixKey==null)==(pixCopyPaste==null))invalid("Provide either pixKey or pixCopyPaste");
                if(pixKey!=null){
                    if(pixKeyType==null||!Set.of("CPF","CNPJ","EMAIL","PHONE","RANDOM").contains(pixKeyType))invalid("A supported pixKeyType is required");
                    if("CPF".equals(pixKeyType)&&!pixKey.matches("[0-9]{11}"))invalid("CPF Pix key must contain 11 digits");
                    if("CNPJ".equals(pixKeyType)&&!pixKey.matches("[0-9]{14}"))invalid("CNPJ Pix key must contain 14 digits");
                    if("PHONE".equals(pixKeyType)&&!pixKey.matches("\\+[1-9][0-9]{7,14}"))invalid("PHONE Pix key must use E.164 format");
                    if("RANDOM".equals(pixKeyType))try{java.util.UUID.fromString(pixKey);}catch(IllegalArgumentException e){invalid("Invalid random Pix key");}
                    if("EMAIL".equals(pixKeyType))try{new jakarta.mail.internet.InternetAddress(pixKey,true).validate();}catch(jakarta.mail.internet.AddressException e){invalid("Invalid email Pix key");}
                }else if(pixKeyType!=null)invalid("pixKeyType requires pixKey");
                absent(digitableLine,barcode,bankCode,bankName,branch,branchCheckDigit,accountNumber,accountCheckDigit,accountType,holderName);
            }
            case BANK_TRANSFER -> {
                if((bankCode==null&&bankName==null)||branch==null||accountNumber==null||holderName==null
                        ||accountType==null||!Set.of("CHECKING","SAVINGS","PAYMENT").contains(accountType))
                    invalid("Bank, branch, accountNumber, accountType and holderName are required");
                absent(digitableLine,barcode,pixKeyType,pixKey,pixCopyPaste);
            }
        }
    }
    private static String text(String value,int max){return PayableCatalogValues.text(value,max,false);}
    private static void absent(String... values){for(String value:values)if(value!=null)invalid("Fields from other payment instruction types are not allowed");}
    private static void invalid(String message){throw new IllegalArgumentException(message);}
    @Override public String toString(){return "PayablePaymentContent[type="+type+", redacted]";}
}
