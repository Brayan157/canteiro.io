package com.renovar.canteiro.io.tenancy.infrastructure;

import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import com.renovar.canteiro.io.shared.infrastructure.web.CorrelationIdFilter;
import com.renovar.canteiro.io.tenancy.application.TenantAuthenticationException;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import com.renovar.canteiro.io.tenancy.application.TenantContextResolver;
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
public class TenantContextAuthenticationFilter extends OncePerRequestFilter {

    private final TenantContextResolver tenantContextResolver;
    private final TenantContextHolder tenantContextHolder;
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
            var tenantContext = tenantContextResolver.resolve(userId);
            if (requiresCompanyTenant(request) && tenantContext.isEmpty()) {
                writeAccessDeniedProblem(response, request);
                return;
            }
            tenantContext.ifPresent(tenantContextHolder::setCurrentTenant);
            filterChain.doFilter(request, response);
        } catch (IllegalArgumentException | TenantAuthenticationException exception) {
            writeUnauthenticatedProblem(response, request);
        } finally {
            tenantContextHolder.clear();
        }
    }

    private void writeUnauthenticatedProblem(HttpServletResponse response, HttpServletRequest request) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,
                "The authenticated user does not have a valid tenant context."
        );
        problem.setType(URI.create("urn:canteiro:problem:unauthenticated"));
        problem.setTitle(ErrorCode.UNAUTHENTICATED.name());
        problem.setProperty("code", ErrorCode.UNAUTHENTICATED.name());
        problem.setProperty("correlationId", CorrelationIdFilter.getCorrelationId(request));
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }

    private boolean requiresCompanyTenant(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        return "/api/v1/company".equals(requestUri) || requestUri.startsWith("/api/v1/company/");
    }

    private void writeAccessDeniedProblem(HttpServletResponse response, HttpServletRequest request) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                "A platform user cannot access a company route."
        );
        problem.setType(URI.create("urn:canteiro:problem:access_denied"));
        problem.setTitle(ErrorCode.ACCESS_DENIED.name());
        problem.setProperty("code", ErrorCode.ACCESS_DENIED.name());
        problem.setProperty("correlationId", CorrelationIdFilter.getCorrelationId(request));
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
