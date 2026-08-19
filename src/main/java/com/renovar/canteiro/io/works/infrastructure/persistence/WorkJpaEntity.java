package com.renovar.canteiro.io.works.infrastructure.persistence;
import com.renovar.canteiro.io.shared.infrastructure.persistence.jpa.BaseJpaEntity;
import com.renovar.canteiro.io.works.domain.WorkStatus;
import com.renovar.canteiro.io.works.domain.WorkExecutionLocationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.util.UUID;
@Getter @Entity @Table(name = "obra") @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkJpaEntity extends BaseJpaEntity {
    @Column(name = "company_id", nullable = false, updatable = false) private UUID companyId;
    @Column(name = "final_customer_id", nullable = false, updatable = false) private UUID finalCustomerId;
    @Column(name = "name", nullable = false, length = 255) private String name;
    @Column(name = "reference", length = 100) private String reference;
    @Enumerated(EnumType.STRING) @Column(name = "execution_location_type", nullable = false, length = 30) private WorkExecutionLocationType executionLocationType;
    @Column(name = "execution_address", length = 500) private String executionAddress;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 20) private WorkStatus status;
    @Column(name = "started_on") private LocalDate startedOn;
    @Column(name = "expected_completion_on") private LocalDate expectedCompletionOn;
    @Column(name = "completed_on") private LocalDate completedOn;
    public WorkJpaEntity(UUID companyId, UUID finalCustomerId, String name, String reference, WorkExecutionLocationType executionLocationType, String executionAddress, WorkStatus status, LocalDate startedOn, LocalDate expectedCompletionOn, LocalDate completedOn) { this.companyId=companyId; this.finalCustomerId=finalCustomerId; this.name=name; this.reference=reference; this.executionLocationType=executionLocationType; this.executionAddress=executionAddress; this.status=status; this.startedOn=startedOn; this.expectedCompletionOn=expectedCompletionOn; this.completedOn=completedOn; }
}
