package com.renovar.canteiro.io.platform.support.infrastructure;

import com.renovar.canteiro.io.platform.support.application.SupportTargetContextHolder;
import com.renovar.canteiro.io.platform.support.application.SupportTargetContextResolver;
import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import com.renovar.canteiro.io.shared.infrastructure.web.CorrelationIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SupportTargetContextFilter extends OncePerRequestFilter {

    public static final String TARGET_COMPANY_ID_HEADER = "X-Canteiro-Target-Company-Id";

    private final SupportTargetContextResolver supportTargetContextResolver;
    private final SupportTargetContextHolder supportTargetContextHolder;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/platform/support/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String targetCompanyId = request.getHeader(TARGET_COMPANY_ID_HEADER);
            if (targetCompanyId == null || targetCompanyId.isBlank()) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        ErrorCode.MALFORMED_REQUEST,
                        "The target company header is required"
                );
            }
            supportTargetContextHolder.setCurrentTarget(supportTargetContextResolver.resolve(UUID.fromString(targetCompanyId)));
            filterChain.doFilter(request, response);
        } catch (AccessDeniedException exception) {
            writeProblem(response, request, HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED, "Access is denied.");
        } catch (IllegalArgumentException exception) {
            writeProblem(response, request, HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_REQUEST, "Target company header is invalid.");
        } catch (ApiException exception) {
            writeProblem(response, request, exception.getStatus(), exception.getErrorCode(), exception.getMessage());
        } finally {
            supportTargetContextHolder.clear();
        }
    }

    private void writeProblem(
            HttpServletResponse response,
            HttpServletRequest request,
            HttpStatus status,
            ErrorCode errorCode,
            String detail
    ) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("urn:canteiro:problem:" + errorCode.name().toLowerCase()));
        problem.setTitle(errorCode.name());
        problem.setProperty("code", errorCode.name());
        problem.setProperty("correlationId", CorrelationIdFilter.getCorrelationId(request));
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
