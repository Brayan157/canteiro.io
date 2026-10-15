package com.renovar.canteiro.io.employees.infrastructure.security;

import com.renovar.canteiro.io.employees.infrastructure.persistence.EmployeePaymentAccountJpaEntity;
import com.renovar.canteiro.io.employees.infrastructure.persistence.EmployeePaymentAccountJpaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-time technical migration. Legacy records were encrypted with the JWT
 * signing secret before payment-data key segregation existed. The legacy key
 * is only used to recipher rows explicitly marked LEGACY_JWT.
 */
@Component
class EmployeePaymentDataKeyMigration implements ApplicationRunner {

    private final EmployeePaymentAccountJpaRepository repository;
    private final EmployeePaymentDataCipher paymentDataCipher;
    private final String jwtSecret;

    EmployeePaymentDataKeyMigration(EmployeePaymentAccountJpaRepository repository,
                                    EmployeePaymentDataCipher paymentDataCipher,
                                    @Value("${security.jwt.secret}") String jwtSecret) {
        this.repository = repository;
        this.paymentDataCipher = paymentDataCipher;
        this.jwtSecret = jwtSecret;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        EmployeePaymentDataCipher legacyCipher = EmployeePaymentDataCipher.legacyJwtCipherForMigration(jwtSecret);
        for (EmployeePaymentAccountJpaEntity account : repository.findByEncryptionKeyVersion(
                EmployeePaymentAccountJpaEntity.LEGACY_JWT_ENCRYPTION_KEY_VERSION)) {
            account.recipher(
                    recipher(account.getAccountNumberCiphertext(), legacyCipher),
                    recipher(account.getAccountCheckDigitCiphertext(), legacyCipher),
                    recipher(account.getPixKeyCiphertext(), legacyCipher),
                    recipher(account.getPixReceiverNameCiphertext(), legacyCipher),
                    recipher(account.getPixReceiverCityCiphertext(), legacyCipher));
        }
    }

    private String recipher(String ciphertext, EmployeePaymentDataCipher legacyCipher) {
        String plainText = legacyCipher.decrypt(ciphertext);
        return plainText == null ? null : paymentDataCipher.encrypt(plainText);
    }
}
