package com.renovar.canteiro.io.works.infrastructure.persistence;
import com.renovar.canteiro.io.works.domain.Work;
import com.renovar.canteiro.io.works.domain.WorkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;
@Repository @RequiredArgsConstructor
public class JpaWorkRepository implements WorkRepository {
    private final WorkJpaRepository repository;
    public Work save(Work work) { WorkJpaEntity e = new WorkJpaEntity(work.getCompanyId(), work.getFinalCustomerId(), work.getName(), work.getReference(), work.getExecutionLocationType(), work.getExecutionAddress(), work.getStatus(), work.getStartedOn(), work.getExpectedCompletionOn(), work.getCompletedOn()); return map(repository.save(e)); }
    public Optional<Work> findByIdAndCompanyId(UUID id, UUID companyId) { return repository.findByIdAndCompanyId(id, companyId).map(this::map); }
    public Page<Work> findByCompanyId(UUID companyId, Pageable pageable) { return repository.findByCompanyId(companyId, pageable).map(this::map); }
    private Work map(WorkJpaEntity e) { return Work.rehydrate(e.getId(), e.getCompanyId(), e.getFinalCustomerId(), e.getName(), e.getReference(), e.getExecutionLocationType(), e.getExecutionAddress(), e.getStatus(), e.getStartedOn(), e.getExpectedCompletionOn(), e.getCompletedOn(), e.getCreatedAt(), e.getUpdatedAt()); }
}
