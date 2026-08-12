package com.renovar.canteiro.io.identity.application;

import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import org.springframework.http.HttpStatus;

public class InvalidAuthenticationException extends ApiException {

    public InvalidAuthenticationException(String message) {
        super(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED, message);
    }
}
