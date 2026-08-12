package com.renovar.canteiro.io.platform.catalog.api.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddPlanBundleItemRequest(@NotNull UUID planId) {
}
