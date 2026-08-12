package com.renovar.canteiro.io.access.application;

import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import org.springframework.http.HttpStatus;

public class TenantResourceNotFoundException extends ApiException {

    public TenantResourceNotFoundException(String resourceName) {
        super(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, resourceName + " was not found");
    }
}
