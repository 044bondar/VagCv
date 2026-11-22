package com.vagcv.garage.scheduling;

import com.vagcv.garage.service.FleetSnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 * Тонкий job нічного знімка автопарку. Стратегія збоїв: "записати і чекати" - без негайних повторів;
 * якщо щось впало, помилка логується, а наступний запуск тригера (наступної ночі) зробить знімок.
 * Ідемпотентність сервісу робить безпечним і ручний повторний запуск.
 */
@Slf4j
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class FleetSnapshotJob implements Job {

    private final FleetSnapshotService service;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            boolean created = service.takeSnapshot();
            log.info("FleetSnapshotJob finished: {}", created ? "snapshot created" : "already existed, skipped");
        } catch (RuntimeException e) {
            log.error("FleetSnapshotJob failed: {} -> no immediate retry, next scheduled run will try again", e.getMessage());
            JobExecutionException wrapped = new JobExecutionException("FleetSnapshotJob failed: " + e.getMessage(), e);
            wrapped.setRefireImmediately(false);
            throw wrapped;
        }
    }
}
