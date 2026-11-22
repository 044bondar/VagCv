package com.vagcv.garage.scheduling;

import com.vagcv.garage.service.StaleRepairService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 * Тонкий job: читає параметри, викликає ОДИН метод сервісу. Бізнес-логіки і транзакції тут немає.
 *
 * Параметри (JobDataMap; значення з trigger перекривають значення з JobDetail):
 *   staleDays        - обов'язковий; відсутній ключ = помилка конфігурації (без retry)
 *   maxRetries       - необов'язковий, default 2: скільки РАЗІВ повторити після збою
 *   simulateFailure  - необов'язковий, default false: штучний збій для демонстрації стратегії помилок
 *
 * Стратегія збоїв (обмежена, без нескінченного циклу): при RuntimeException job просить Quartz
 * повторити його негайно (refireImmediately), але не більше maxRetries разів
 * (лічильник - context.getRefireCount()). Після вичерпання спроб - лог ERROR і чекаємо наступного
 * планового спрацювання тригера.
 *
 * @DisallowConcurrentExecution: два екземпляри ЦЬОГО JobDetail не виконуються одночасно
 * (напр. 2-хвилинний demo-тригер не накладеться на ще не завершений запуск).
 */
@Slf4j
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class StaleRepairJob implements Job {

    public static final String KEY_STALE_DAYS = "staleDays";
    public static final String KEY_MAX_RETRIES = "maxRetries";
    public static final String KEY_SIMULATE_FAILURE = "simulateFailure";
    public static final int DEFAULT_MAX_RETRIES = 2;

    private final StaleRepairService service;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap data = context.getMergedJobDataMap();            // job defaults + trigger overrides
        int staleDays = JobParams.requiredNonNegativeInt(data, KEY_STALE_DAYS);
        int maxRetries = JobParams.intOrDefault(data, KEY_MAX_RETRIES, DEFAULT_MAX_RETRIES);
        boolean simulateFailure = JobParams.booleanOrDefault(data, KEY_SIMULATE_FAILURE, false);
        int attempt = context.getRefireCount();

        log.info("StaleRepairJob started: trigger={}, staleDays={}, attempt={}/{}",
                triggerName(context), staleDays, attempt + 1, maxRetries + 1);
        try {
            if (simulateFailure) {
                throw new IllegalStateException("Simulated failure (simulateFailure=true)");
            }
            int created = service.createReminders(staleDays);
            log.info("StaleRepairJob finished: {} new reminder(s)", created);
        } catch (RuntimeException e) {
            JobExecutionException wrapped = new JobExecutionException("StaleRepairJob failed: " + e.getMessage(), e);
            if (attempt < maxRetries) {
                log.warn("StaleRepairJob failed (attempt {}/{}): {} -> retry immediately",
                        attempt + 1, maxRetries + 1, e.getMessage());
                wrapped.setRefireImmediately(true);
            } else {
                log.error("StaleRepairJob failed (attempt {}/{}): {} -> retries exhausted, waiting for the next trigger",
                        attempt + 1, maxRetries + 1, e.getMessage());
                wrapped.setRefireImmediately(false);
            }
            throw wrapped;
        }
    }

    private static String triggerName(JobExecutionContext context) {
        return context.getTrigger() == null ? "?" : context.getTrigger().getKey().toString();
    }
}
