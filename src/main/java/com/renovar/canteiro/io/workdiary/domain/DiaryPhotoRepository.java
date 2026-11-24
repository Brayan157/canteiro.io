package com.renovar.canteiro.io.workdiary.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiaryPhotoRepository {
    Optional<Day> findDay(UUID company, UUID work, LocalDate date);

    boolean revisionExists(UUID company, UUID work, UUID diary, long revision);

    boolean itemExists(UUID company, UUID work, UUID diary, long revision, DiaryPhoto.Target target);

    List<DiaryPhoto> list(UUID company, UUID work, UUID diary, Long revision, DiaryPhoto.Target target, boolean includeWithdrawn,
                          int offset, int limit);

    Optional<DiaryPhoto> find(UUID company, UUID work, UUID diary, UUID photo);

    Optional<DiaryPhoto> findByOperation(UUID company, UUID work, UUID diary, UUID operation);

    void insert(UUID company, UUID work, UUID diary, DiaryPhoto photo);

    void withdraw(UUID company, UUID work, UUID diary, UUID photo, UUID actor, Instant at, String reason);

    record Day(UUID id, long revision, boolean closed) {
    }
}
