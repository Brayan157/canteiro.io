package com.renovar.canteiro.io.shared.api.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.List;

@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode errorCode;
    private final List<FieldViolation> violations;
    private final String reason;

    public ApiException(HttpStatus status, ErrorCode errorCode, String message) {
        this(status, errorCode, message, List.of());
    }

    public ApiException(HttpStatus status, ErrorCode errorCode, String message, List<FieldViolation> violations) {
        this(status, errorCode, message, violations, null);
    }

    public ApiException(HttpStatus status, ErrorCode errorCode, String message, List<FieldViolation> violations, String reason) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
        this.violations = List.copyOf(violations);
        this.reason = reason;
    }
}
