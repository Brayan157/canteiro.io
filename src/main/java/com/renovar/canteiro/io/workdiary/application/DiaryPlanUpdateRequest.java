package com.renovar.canteiro.io.workdiary.application;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record DiaryPlanUpdateRequest(@NotNull UUID operationId, @NotNull @PositiveOrZero Long expectedVersion,
                                     @NotBlank @Size(max = 500) String reason,
                                     @NotNull @Size(max = 100) List<@NotNull @Valid Schedule> schedules) {
    public record Schedule(UUID serviceId, @PositiveOrZero Long plannedPersonMinutes,
                           @JsonFormat(without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE) OffsetDateTime plannedStart,
                           @JsonFormat(without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE) OffsetDateTime plannedEnd,
                           @NotNull @Size(max = 100) List<@NotNull @Valid Milestone> milestones) {
    }

    public record Milestone(@NotNull UUID id, @NotBlank @Size(max = 200) String title, @NotNull LocalDate dueOn,
                            LocalDate completionDiaryDate, @PositiveOrZero Long completionDiaryRevision) {
    }
}
