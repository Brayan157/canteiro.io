package com.renovar.canteiro.io.tenancy.application;

import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import org.springframework.http.HttpStatus;

public class TenantAuthenticationException extends ApiException {

    public TenantAuthenticationException(String message) {
        super(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED, message);
    }
}
