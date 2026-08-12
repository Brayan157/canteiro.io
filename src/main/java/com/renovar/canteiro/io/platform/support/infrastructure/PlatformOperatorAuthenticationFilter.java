package com.renovar.canteiro.io.platform.support.infrastructure;

import com.renovar.canteiro.io.platform.support.application.PlatformAuthenticationException;
import com.renovar.canteiro.io.platform.support.application.PlatformOperatorContextHolder;
import com.renovar.canteiro.io.platform.support.application.PlatformOperatorContextResolver;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PlatformOperatorAuthenticationFilter extends OncePerRequestFilter {

    private final PlatformOperatorContextResolver platformOperatorContextResolver;
    private final PlatformOperatorContextHolder platformOperatorContextHolder;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (!(authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) || !authentication.isAuthenticated()) {
                filterChain.doFilter(request, response);
                return;
            }
            UUID userId = UUID.fromString(jwtAuthenticationToken.getToken().getSubject());
            platformOperatorContextResolver.resolve(userId).ifPresent(platformOperatorContextHolder::setCurrentOperator);
            filterChain.doFilter(request, response);
        } catch (IllegalArgumentException | PlatformAuthenticationException exception) {
            writeUnauthenticatedProblem(response, request);
        } finally {
            platformOperatorContextHolder.clear();
        }
    }

    private void writeUnauthenticatedProblem(HttpServletResponse response, HttpServletRequest request) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,
                "The authenticated user does not have a valid platform context."
        );
        problem.setType(URI.create("urn:canteiro:problem:unauthenticated"));
        problem.setTitle(ErrorCode.UNAUTHENTICATED.name());
        problem.setProperty("code", ErrorCode.UNAUTHENTICATED.name());
        problem.setProperty("correlationId", CorrelationIdFilter.getCorrelationId(request));
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
