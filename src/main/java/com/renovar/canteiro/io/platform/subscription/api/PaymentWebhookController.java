package com.renovar.canteiro.io.platform.subscription.api;

import com.renovar.canteiro.io.platform.subscription.application.PaymentGatewayWebhookRequest;
import com.renovar.canteiro.io.platform.subscription.application.PaymentWebhookApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/webhooks/payments")
@SecurityRequirements
@ConditionalOnProperty(prefix = "integrations.asaas", name = "enabled", havingValue = "true")
public class PaymentWebhookController {

    private final PaymentWebhookApplicationService applicationService;
    private final Clock clock;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Receive an authenticated payment gateway webhook", parameters = @Parameter(
            name = "asaas-access-token", in = ParameterIn.HEADER, required = true,
            description = "Shared webhook token configured for the Asaas sandbox"
    ))
    public ResponseEntity<Void> receive(
            @RequestBody byte[] rawPayload,
            @Parameter(hidden = true) @RequestHeader Map<String, String> headers
    ) {
        applicationService.receive(new PaymentGatewayWebhookRequest(rawPayload, headers, clock.instant()));
        return ResponseEntity.ok().build();
    }
}
