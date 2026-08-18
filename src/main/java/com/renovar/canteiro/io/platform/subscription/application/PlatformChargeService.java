package com.renovar.canteiro.io.platform.subscription.application;

import com.renovar.canteiro.io.platform.subscription.domain.PaymentGatewayProviderCode;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformCharge;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeRepository;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeStatus;
import com.renovar.canteiro.io.platform.subscription.domain.Subscription;
import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnBean(PaymentGateway.class)
@RequiredArgsConstructor
public class PlatformChargeService {

    private final PaymentGateway paymentGateway;
    private final SubscriptionRepository subscriptionRepository;
    private final PlatformChargeRepository platformChargeRepository;

    @Transactional
    public PlatformCharge createCharge(PaymentGatewayChargeRequest request) {
        PaymentGatewayProviderCode provider = paymentGateway.providerCode();
        platformChargeRepository.lockIdempotencyKey(
                provider, request.idempotencyKey()
        );
        return platformChargeRepository.findByProviderAndIdempotencyKey(
                        provider, request.idempotencyKey()
                )
                .map(existing -> requireSameRequest(existing, request))
                .orElseGet(() -> createNewCharge(provider, request));
    }

    private PlatformCharge createNewCharge(
            PaymentGatewayProviderCode provider,
            PaymentGatewayChargeRequest request
    ) {
        Subscription subscription = subscriptionRepository.findById(request.subscriptionId())
                .orElseThrow(() -> new IllegalArgumentException("Charge subscription does not exist"));
        PaymentGatewayChargeResult result = paymentGateway.createCharge(request);
        return platformChargeRepository.save(PlatformCharge.create(
                subscription.getCompanyId(),
                subscription.getId(),
                provider,
                request.idempotencyKey(),
                request.externalCustomerId(),
                result.externalChargeId(),
                request.billingMethod(),
                request.amount(),
                request.dueDate(),
                toPlatformStatus(result.status())
        ));
    }

    private PlatformCharge requireSameRequest(PlatformCharge charge, PaymentGatewayChargeRequest request) {
        if (!charge.matches(
                request.subscriptionId(), request.externalCustomerId(), request.billingMethod(),
                request.amount(), request.dueDate()
        )) {
            throw new IllegalArgumentException("Idempotency key was already used for a different charge request");
        }
        return charge;
    }

    private PlatformChargeStatus toPlatformStatus(PaymentGatewayChargeStatus status) {
        return switch (status) {
            case PENDING -> PlatformChargeStatus.PENDING;
            case CONFIRMED -> PlatformChargeStatus.CONFIRMED;
            case OVERDUE -> PlatformChargeStatus.OVERDUE;
            case CANCELLED -> PlatformChargeStatus.CANCELLED;
        };
    }
}
