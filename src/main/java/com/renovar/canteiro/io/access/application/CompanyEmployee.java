package com.renovar.canteiro.io.access.application;

import com.renovar.canteiro.io.identity.domain.User;

import java.util.Set;
import java.util.UUID;

public record CompanyEmployee(User user, Set<UUID> roleIds) {
}
