package com.renovar.canteiro.io.governance.application;

import java.util.UUID;

public record AppliedChangeRequestEntity(String type, UUID id, String displayName) {
}
