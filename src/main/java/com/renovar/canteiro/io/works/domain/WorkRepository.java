package com.renovar.canteiro.io.works.domain;
import java.util.Optional;
import java.util.UUID;
public interface WorkRepository { Work save(Work work); Optional<Work> findByIdAndCompanyId(UUID id, UUID companyId); }
