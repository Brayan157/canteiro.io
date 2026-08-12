package com.renovar.canteiro.io.governance.application;

import java.util.UUID;

public record ApproveChangeRequestCommand(UUID changeRequestId, String decisionReason) {
}
