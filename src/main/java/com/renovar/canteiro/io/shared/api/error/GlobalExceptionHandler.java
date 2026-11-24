package com.renovar.canteiro.io.shared.api.error;

import com.renovar.canteiro.io.shared.infrastructure.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.renovar.canteiro.io.platform.subscription.application.PaymentGatewayWebhookAuthenticationException;

import java.net.URI;
import java.util.List;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ProblemDetail handleApiException(ApiException exception, HttpServletRequest request) {
        ProblemDetail result = problem(exception.getStatus(), exception.getErrorCode(), exception.getMessage(), request);
        if (!exception.getViolations().isEmpty()) result.setProperty("violations", exception.getViolations());
        if (exception.getReason() != null) result.setProperty("reason", exception.getReason());
        return result;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<FieldViolation> violations = exception.getBindingResult().getFieldErrors().stream()
                .map(this::toViolation)
                .toList();
        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_FAILED,
                "One or more fields are invalid.",
                request
        );
        problem.setProperty("violations", violations);
        return problem;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail handleInvalidParameterType(MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                "One or more request parameters have invalid types.", request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraintViolation(ConstraintViolationException exception, HttpServletRequest request) {
        List<FieldViolation> violations = exception.getConstraintViolations().stream()
                .map(violation -> new FieldViolation(violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_FAILED,
                "One or more parameters are invalid.",
                request
        );
        problem.setProperty("violations", violations);
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadableMessage(HttpMessageNotReadableException exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_REQUEST, "Request body is invalid.", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException exception, HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED, "Access is denied.", request);
    }

    @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
    ProblemDetail handleMissingParameter(org.springframework.web.bind.MissingServletRequestParameterException exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_REQUEST, "A required request parameter is missing.", request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleIllegalArgument(IllegalArgumentException exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, exception.getMessage(), request);
    }

    @ExceptionHandler(com.renovar.canteiro.io.payables.domain.PayableCatalogConflictException.class)
    ProblemDetail handlePayableCatalogConflict(com.renovar.canteiro.io.payables.domain.PayableCatalogConflictException exception, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, ErrorCode.BUSINESS_RULE_VIOLATION, exception.getMessage(), request);
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail handleIllegalState(IllegalStateException exception, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, ErrorCode.BUSINESS_RULE_VIOLATION, exception.getMessage(), request);
    }

    @ExceptionHandler(com.renovar.canteiro.io.receivables.domain.InvoiceServiceCoverageViolation.class)
    ProblemDetail handleInvoiceCoverage(com.renovar.canteiro.io.receivables.domain.InvoiceServiceCoverageViolation exception,HttpServletRequest request){
        ProblemDetail value=problem(HttpStatus.CONFLICT,ErrorCode.BUSINESS_RULE_VIOLATION,exception.getMessage(),request);
        value.setProperty("reason",exception.reason());return value;
    }

    @ExceptionHandler(com.renovar.canteiro.io.receivables.domain.InvoiceFiscalIdentityConflict.class)
    ProblemDetail handleInvoiceIdentity(com.renovar.canteiro.io.receivables.domain.InvoiceFiscalIdentityConflict exception,HttpServletRequest request){
        ProblemDetail value=problem(HttpStatus.CONFLICT,ErrorCode.BUSINESS_RULE_VIOLATION,exception.getMessage(),request);
        value.setProperty("reason","INVOICE_FISCAL_IDENTITY_ALREADY_REGISTERED");return value;
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    ProblemDetail handleUploadLimit(org.springframework.web.multipart.MaxUploadSizeExceededException exception,HttpServletRequest request){
        return problem(HttpStatus.valueOf(413),ErrorCode.VALIDATION_FAILED,"Upload exceeds the configured request size limit.",request);
    }

    @ExceptionHandler(org.springframework.web.multipart.support.MissingServletRequestPartException.class)
    ProblemDetail handleMissingUpload(org.springframework.web.multipart.support.MissingServletRequestPartException exception,HttpServletRequest request){
        return problem(HttpStatus.BAD_REQUEST,ErrorCode.MALFORMED_REQUEST,"A required multipart field is missing.",request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleOptimisticLockingFailure(OptimisticLockingFailureException exception, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, ErrorCode.BUSINESS_RULE_VIOLATION,
                "The contract billing balance changed concurrently. Retry the operation.", request);
    }

    @ExceptionHandler(PaymentGatewayWebhookAuthenticationException.class)
    ProblemDetail handleWebhookAuthentication(
            PaymentGatewayWebhookAuthenticationException exception, HttpServletRequest request
    ) {
        return problem(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED, "Webhook authentication failed.", request);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception, HttpServletRequest request) {
        // Do not place exception details in the response. The log has the full causal chain
        // and the correlation id required to investigate it without disclosing internals.
        LOGGER.error("Unhandled API exception [correlationId={}, method={}, path={}]",
                CorrelationIdFilter.getCorrelationId(request), request.getMethod(), request.getRequestURI(), exception);
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred.",
                request
        );
    }

    private FieldViolation toViolation(FieldError error) {
        return new FieldViolation(error.getField(), error.getDefaultMessage());
    }

    private ProblemDetail problem(HttpStatus status, ErrorCode errorCode, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("urn:canteiro:problem:" + errorCode.name().toLowerCase()));
        problem.setTitle(errorCode.name());
        problem.setProperty("code", errorCode.name());
        problem.setProperty("correlationId", CorrelationIdFilter.getCorrelationId(request));
        return problem;
    }
}
