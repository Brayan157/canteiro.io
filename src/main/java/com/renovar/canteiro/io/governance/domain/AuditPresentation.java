package com.renovar.canteiro.io.governance.domain;

public record AuditPresentation(String actorDisplayName, String entityDisplayName, String entityDisplayReference,
                                String summary, String origin, String supportContext, String correlationId) { }
