package com.vagcv.garage.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vagcv.garage.service.StaleRepairService;
import org.junit.jupiter.api.Test;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/** Job тонкий: тестуємо лише читання параметрів і стратегію збоїв; сервіс - мок. */
class StaleRepairJobTest {

    private final StaleRepairService service = mock(StaleRepairService.class);
    private final StaleRepairJob job = new StaleRepairJob(service);

    private JobExecutionContext ctx(JobDataMap data, int refireCount) {
        JobExecutionContext ctx = mock(JobExecutionContext.class);
        when(ctx.getMergedJobDataMap()).thenReturn(data);
        when(ctx.getRefireCount()).thenReturn(refireCount);
        return ctx;
    }

    private static JobDataMap map(Object... kv) {
        JobDataMap m = new JobDataMap();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    void callsServiceOnceWithStaleDaysFromMergedMap() throws Exception {
        job.execute(ctx(map("staleDays", 3), 0));
        verify(service).createReminders(3);
    }

    @Test
    void missingRequiredKeyFailsExplicitlyWithoutRetryAndWithoutCallingService() {
        assertThatThrownBy(() -> job.execute(ctx(map("maxRetries", 2), 0)))
                .isInstanceOf(JobExecutionException.class)
                .hasMessageContaining("Missing required JobDataMap key 'staleDays'");
        verify(service, never()).createReminders(anyInt());
    }

    @Test
    void misspelledKeyIsTreatedAsMissing() {
        assertThatThrownBy(() -> job.execute(ctx(map("stalDays", 5), 0)))      // помилка в назві ключа
                .isInstanceOf(JobExecutionException.class)
                .hasMessageContaining("staleDays");
        verify(service, never()).createReminders(anyInt());
    }

    @Test
    void negativeValueIsRejected() {
        assertThatThrownBy(() -> job.execute(ctx(map("staleDays", -1), 0)))
                .isInstanceOf(JobExecutionException.class)
                .hasMessageContaining(">= 0");
    }

    @Test
    void failureWithinRetryBudgetAsksQuartzToRefireImmediately() {
        when(service.createReminders(anyInt())).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> job.execute(ctx(map("staleDays", 7, "maxRetries", 2), 0)))
                .isInstanceOfSatisfying(JobExecutionException.class,
                        e -> assertThat(e.refireImmediately()).isTrue());
    }

    @Test
    void failureAfterRetriesExhaustedDoesNotRefire_noInfiniteLoop() {
        when(service.createReminders(anyInt())).thenThrow(new IllegalStateException("db down"));

        // refireCount == maxRetries -> всі спроби використано
        assertThatThrownBy(() -> job.execute(ctx(map("staleDays", 7, "maxRetries", 2), 2)))
                .isInstanceOfSatisfying(JobExecutionException.class,
                        e -> assertThat(e.refireImmediately()).isFalse());
    }

    @Test
    void maxRetriesDefaultsToTwoWhenKeyIsAbsent() {
        when(service.createReminders(anyInt())).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> job.execute(ctx(map("staleDays", 7), 1)))      // 1 < default 2 -> ще можна
                .isInstanceOfSatisfying(JobExecutionException.class,
                        e -> assertThat(e.refireImmediately()).isTrue());
        assertThatThrownBy(() -> job.execute(ctx(map("staleDays", 7), 2)))      // 2 == default -> все
                .isInstanceOfSatisfying(JobExecutionException.class,
                        e -> assertThat(e.refireImmediately()).isFalse());
    }

    @Test
    void simulateFailureFlagFailsBeforeCallingService() {
        assertThatThrownBy(() -> job.execute(ctx(map("staleDays", 7, "simulateFailure", true), 0)))
                .isInstanceOf(JobExecutionException.class);
        verify(service, never()).createReminders(anyInt());
    }
}
