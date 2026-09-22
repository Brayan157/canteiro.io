package com.renovar.canteiro.io.contracts.api.response;

import java.util.UUID;

public record ServiceTemplateResponse(UUID id, String name, String description, boolean active) {
}
