package com.renovar.canteiro.io.identity.infrastructure.security;

import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import com.renovar.canteiro.io.shared.infrastructure.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;

@Component
@RequiredArgsConstructor
public class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException {
        // Preserve the standard Bearer challenge, without serializing token/exception content.
        new BearerTokenAuthenticationEntryPoint().commence(request, response, exception);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Authentication is required.");
        problem.setType(URI.create("urn:canteiro:problem:unauthenticated"));
        problem.setTitle(ErrorCode.UNAUTHENTICATED.name());
        problem.setProperty("code", ErrorCode.UNAUTHENTICATED.name());
        problem.setProperty("correlationId", CorrelationIdFilter.getCorrelationId(request));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
