package com.renovar.canteiro.io.platform.support.infrastructure;

import com.renovar.canteiro.io.platform.support.application.PlatformOperatorContextHolder;
import com.renovar.canteiro.io.platform.support.domain.PlatformOperatorContext;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ThreadLocalPlatformOperatorContextHolder implements PlatformOperatorContextHolder {

    private final ThreadLocal<PlatformOperatorContext> platformOperatorContext = new ThreadLocal<>();

    @Override
    public Optional<PlatformOperatorContext> currentOperator() {
        return Optional.ofNullable(platformOperatorContext.get());
    }

    @Override
    public void setCurrentOperator(PlatformOperatorContext currentPlatformOperatorContext) {
        platformOperatorContext.set(currentPlatformOperatorContext);
    }

    @Override
    public void clear() {
        platformOperatorContext.remove();
    }
}
