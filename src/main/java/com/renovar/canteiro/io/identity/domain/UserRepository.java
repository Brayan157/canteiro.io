package com.renovar.canteiro.io.identity.domain;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface UserRepository {

    User save(User user);

    Optional<User> findById(UUID id);

    Optional<User> findByEmail(String email);

    List<User> findAllByIds(Set<UUID> ids);
}
