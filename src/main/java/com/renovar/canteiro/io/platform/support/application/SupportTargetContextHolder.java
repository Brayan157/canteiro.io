package com.renovar.canteiro.io.platform.support.application;

import com.renovar.canteiro.io.platform.support.domain.SupportTargetContext;

import java.util.Optional;

public interface SupportTargetContextHolder {

    Optional<SupportTargetContext> currentTarget();

    SupportTargetContext requireCurrentTarget();

    void setCurrentTarget(SupportTargetContext supportTargetContext);

    void clear();
}
