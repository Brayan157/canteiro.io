package com.renovar.canteiro.io.platform.support.application;

import com.renovar.canteiro.io.platform.support.domain.PlatformOperatorContext;

import java.util.Optional;

public interface PlatformOperatorContextHolder {

    Optional<PlatformOperatorContext> currentOperator();

    void setCurrentOperator(PlatformOperatorContext platformOperatorContext);

    void clear();
}
