package com.renovar.canteiro.io.governance.application;

import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import org.springframework.http.HttpStatus;

public class ChangeRequestNotFoundException extends ApiException {

    public ChangeRequestNotFoundException() {
        super(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "Change request not found");
    }
}
