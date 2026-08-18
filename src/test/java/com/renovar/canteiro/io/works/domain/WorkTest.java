package com.renovar.canteiro.io.works.domain;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertThrows;
class WorkTest {
 @Test void rejectsCompletionBeforeStart() { assertThrows(IllegalArgumentException.class, () -> Work.create(UUID.randomUUID(), UUID.randomUUID(), "Work", null, WorkExecutionLocationType.FINAL_CUSTOMER_LOCATION, null, WorkStatus.ACTIVE, LocalDate.of(2026, 8, 2), null, LocalDate.of(2026, 8, 1))); }
 @Test void requiresAddressForOtherLocation() { assertThrows(IllegalArgumentException.class, () -> Work.create(UUID.randomUUID(), UUID.randomUUID(), "Work", null, WorkExecutionLocationType.OTHER_ADDRESS, null, WorkStatus.DRAFT, null, null, null)); }
}
