package com.renovar.canteiro.io.platform.subscription.infrastructure;

import com.renovar.canteiro.io.platform.subscription.application.CompanySubscriptionAccessService;
import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionAccessLevel;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import com.renovar.canteiro.io.shared.infrastructure.web.CorrelationIdFilter;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class SubscriptionAccessFilter extends OncePerRequestFilter {

    private static final String COMPANY_ROUTE = "/api/v1/company";
    private static final Set<String> READ_ONLY_METHODS = Set.of(
            HttpMethod.GET.name(), HttpMethod.HEAD.name(), HttpMethod.OPTIONS.name()
    );

    private final TenantContextHolder tenantContextHolder;
    private final CompanySubscriptionAccessService companySubscriptionAccessService;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !isCompanyRoute(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        SubscriptionAccessLevel accessLevel = companySubscriptionAccessService.resolveAccessLevel(
                tenantContextHolder.requireCurrentTenant().companyId()
        );
        if (accessLevel == SubscriptionAccessLevel.BLOCKED) {
            writeAccessDenied(response, request, "Company access is blocked because of overdue subscription charges.");
            return;
        }
        if (isReadOnly(accessLevel) && !READ_ONLY_METHODS.contains(request.getMethod())) {
            writeAccessDenied(response, request, "Company access is limited to read-only operations because of overdue subscription charges.");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isReadOnly(SubscriptionAccessLevel accessLevel) {
        return accessLevel == SubscriptionAccessLevel.READ_ONLY
                || accessLevel == SubscriptionAccessLevel.DELINQUENT_READ_ONLY;
    }

    private boolean isCompanyRoute(String requestUri) {
        return COMPANY_ROUTE.equals(requestUri) || requestUri.startsWith(COMPANY_ROUTE + "/");
    }

    private void writeAccessDenied(
            HttpServletResponse response,
            HttpServletRequest request,
            String detail
    ) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, detail);
        problem.setType(URI.create("urn:canteiro:problem:access_denied"));
        problem.setTitle(ErrorCode.ACCESS_DENIED.name());
        problem.setProperty("code", ErrorCode.ACCESS_DENIED.name());
        problem.setProperty("correlationId", CorrelationIdFilter.getCorrelationId(request));
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
