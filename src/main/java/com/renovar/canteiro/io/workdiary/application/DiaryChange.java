package com.renovar.canteiro.io.workdiary.application;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.renovar.canteiro.io.workdiary.domain.WorkDiary.WeatherCondition;
import com.renovar.canteiro.io.workforce.domain.WorkOperation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record DiaryChange(@NotNull UUID operationId, @PositiveOrZero Long expectedRevision, @Size(max = 500) String reason,
                          @Size(max = 500) String noExecutionReason,
                          @Size(max = 100) List<@NotNull @Valid Activity> activities,
                          @Size(max = 100) List<@NotNull @Valid Occurrence> occurrences,
                          @JsonInclude(JsonInclude.Include.NON_NULL)
                          @Size(max = 3) List<@NotNull WeatherCondition> weatherConditions) {
    public record Activity(@NotNull UUID id, UUID serviceId, @NotBlank @Size(max = 1000) String description,
                           @Size(max = 120) String shift, @Size(max = 120) String front,
                           @NotNull @Size(max = 100) List<@NotNull @Valid Entry> entries,
                           @JsonInclude(JsonInclude.Include.NON_NULL) @Valid ActivityTiming timing,
                           @JsonInclude(JsonInclude.Include.NON_NULL)
                           @Size(max = 100) List<@NotNull @Valid Participant> participants) {
    }

    public record ActivityTiming(
            @NotNull @JsonFormat(without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE) OffsetDateTime start,
            @JsonFormat(without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE) OffsetDateTime end) {
    }

    public record Participant(@NotNull UUID employeeId, @Size(max = 500) String occasionalReason) {
    }

    public record Entry(@NotNull UUID id, @NotNull UUID employeeId, @Positive Integer minutes,
                        @JsonFormat(without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE) OffsetDateTime start,
                        @JsonFormat(without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE) OffsetDateTime end,
                        @Size(max = 500) String occasionalReason) {
    }

    @Schema(name = "DiaryOccurrenceChange")
    public record Occurrence(@NotNull UUID id, UUID activityId, @NotBlank @Size(max = 1000) String description,
                             @Size(max = 500) String delayReason,
                             @JsonInclude(JsonInclude.Include.NON_NULL) @Valid Stoppage stoppage,
                             @JsonInclude(JsonInclude.Include.NON_NULL) Boolean clearStoppage,
                             @JsonInclude(JsonInclude.Include.NON_NULL) @Valid ActivityTiming timing) {
    }

    @Schema(name = "DiaryStoppageChange")
    public record Stoppage(
            @NotNull @JsonFormat(without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE) OffsetDateTime start,
            @JsonFormat(without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE) OffsetDateTime resumedAt,
            @JsonFormat(without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE) OffsetDateTime crewDepartedAt) {
    }

    public record Command(UUID workId, java.time.LocalDate date, WorkOperation operation, DiaryChange change) {
    }
}
