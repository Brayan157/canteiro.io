package com.renovar.canteiro.io.governance.application;

import java.util.UUID;

public record ChangeRequestEntityProjection(String type, UUID id, String displayName) {
}
