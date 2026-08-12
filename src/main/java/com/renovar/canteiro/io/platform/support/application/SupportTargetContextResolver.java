package com.renovar.canteiro.io.platform.support.application;

import com.renovar.canteiro.io.platform.company.domain.CompanyRepository;
import com.renovar.canteiro.io.platform.support.domain.PlatformOperatorContext;
import com.renovar.canteiro.io.platform.support.domain.SupportTargetContext;
import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SupportTargetContextResolver {

    private final PlatformOperatorContextHolder platformOperatorContextHolder;
    private final CompanyRepository companyRepository;

    @Transactional(readOnly = true)
    public SupportTargetContext resolve(UUID targetCompanyId) {
        PlatformOperatorContext operator = platformOperatorContextHolder.currentOperator()
                .orElseThrow(() -> new AccessDeniedException("A platform support operator is required"));
        companyRepository.findById(targetCompanyId)
                .filter(company -> company.isActive())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Target company does not exist"
                ));
        return new SupportTargetContext(
                operator.userId(),
                operator.platformUserId(),
                operator.globalRole(),
                targetCompanyId
        );
    }
}
