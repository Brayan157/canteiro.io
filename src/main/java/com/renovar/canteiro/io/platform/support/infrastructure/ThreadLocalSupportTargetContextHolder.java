package com.renovar.canteiro.io.platform.support.infrastructure;

import com.renovar.canteiro.io.platform.support.application.SupportTargetContextHolder;
import com.renovar.canteiro.io.platform.support.domain.SupportTargetContext;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ThreadLocalSupportTargetContextHolder implements SupportTargetContextHolder {

    private final ThreadLocal<SupportTargetContext> supportTargetContext = new ThreadLocal<>();

    @Override
    public Optional<SupportTargetContext> currentTarget() {
        return Optional.ofNullable(supportTargetContext.get());
    }

    @Override
    public SupportTargetContext requireCurrentTarget() {
        return currentTarget().orElseThrow(() -> new IllegalStateException("A platform support target context is required"));
    }

    @Override
    public void setCurrentTarget(SupportTargetContext currentSupportTargetContext) {
        supportTargetContext.set(currentSupportTargetContext);
    }

    @Override
    public void clear() {
        supportTargetContext.remove();
    }
}
