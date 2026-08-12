package com.renovar.canteiro.io.governance.application;

import java.util.UUID;

public record RejectChangeRequestCommand(UUID changeRequestId, String decisionReason) {
}
