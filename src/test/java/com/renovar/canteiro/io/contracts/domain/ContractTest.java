package com.renovar.canteiro.io.contracts.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ContractTest {

    @Test
    void requiresAWork() {
        assertThrows(IllegalArgumentException.class, () -> Contract.create(
                UUID.randomUUID(), null, null, "Commercial agreement", ContractStatus.DRAFT, null, null, null
        ));
    }
}
