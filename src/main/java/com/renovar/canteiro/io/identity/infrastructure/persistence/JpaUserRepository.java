package com.renovar.canteiro.io.identity.infrastructure.persistence;

import com.renovar.canteiro.io.identity.domain.User;
import com.renovar.canteiro.io.identity.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaUserRepository implements UserRepository {

    private final UserJpaRepository userJpaRepository;
    private final UserPersistenceMapper userPersistenceMapper;

    @Override
    public User save(User user) {
        if (user.getId() == null) {
            return userPersistenceMapper.toDomain(userJpaRepository.save(userPersistenceMapper.toJpaEntity(user)));
        }

        UserJpaEntity entity = userJpaRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalStateException("User must exist before it can be updated"));
        userPersistenceMapper.updateJpaEntity(entity, user);
        return userPersistenceMapper.toDomain(userJpaRepository.save(entity));
    }

    @Override
    public Optional<User> findById(UUID id) {
        return userJpaRepository.findById(id).map(userPersistenceMapper::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return userJpaRepository.findByEmailIgnoreCase(email).map(userPersistenceMapper::toDomain);
    }

    @Override
    public List<User> findAllByIds(Set<UUID> ids) {
        return userJpaRepository.findByIdIn(ids).stream().map(userPersistenceMapper::toDomain).toList();
    }
}
