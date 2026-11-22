package com.vagcv.garage.scheduling;

import static com.vagcv.garage.scheduling.QuartzNames.FLEET_SNAPSHOT_JOB;
import static com.vagcv.garage.scheduling.QuartzNames.FLEET_SNAPSHOT_NIGHTLY;
import static com.vagcv.garage.scheduling.QuartzNames.STALE_REPAIR_DAILY;
import static com.vagcv.garage.scheduling.QuartzNames.STALE_REPAIR_DEMO;
import static com.vagcv.garage.scheduling.QuartzNames.STALE_REPAIR_JOB;

import com.vagcv.garage.service.StaleRepairService;
import java.util.TimeZone;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Єдине місце, де описані jobs і triggers. Spring Boot сам реєструє всі бінe JobDetail/Trigger у планувальнику
 * (JDBC job store -> записуються в QRTZ_* таблиці; overwrite-existing-jobs=true оновлює їх при зміні коду).
 *
 *  JOB 1  stale-repair-reminder  (2 тригери!)
 *     JobDetail-defaults: staleDays=7, maxRetries=2
 *     TRIGGER stale-repair-daily      щодня 09:00 Europe/Kyiv, staleDays береться з job (=7), misfire = FIRE_ONCE_NOW
 *     TRIGGER stale-repair-demo-2min  кожні 2 хв (для демо), перекриває staleDays=0, misfire = DO_NOTHING
 *  JOB 2  fleet-snapshot
 *     TRIGGER fleet-snapshot-nightly  щодня 23:30 Europe/Kyiv, misfire = FIRE_ONCE_NOW
 */
@Configuration
public class QuartzConfig {

    /** Явний часовий пояс cron-тригерів (не залежить від налаштувань сервера). */
    public static final TimeZone KYIV = TimeZone.getTimeZone(StaleRepairService.BUSINESS_ZONE);

    // ---------------------------------------------------------------- JOB 1
    @Bean
    public JobDetail staleRepairJobDetail() {
        return JobBuilder.newJob(StaleRepairJob.class)
                .withIdentity(STALE_REPAIR_JOB)
                .withDescription("Створює нагадування для авто, що надто довго в ремонті")
                .usingJobData(StaleRepairJob.KEY_STALE_DAYS, 7)          // значення за замовчуванням
                .usingJobData(StaleRepairJob.KEY_MAX_RETRIES, 2)
                .storeDurably()                                          // job живе в БД і без тригерів
                .build();
    }

    @Bean
    public Trigger staleRepairDailyTrigger() {
        return TriggerBuilder.newTrigger()
                .forJob(STALE_REPAIR_JOB)                                // прив'язка до job через JobKey
                .withIdentity(STALE_REPAIR_DAILY)
                .withDescription("Щодня о 09:00 за Києвом")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 9 * * ?")
                        .inTimeZone(KYIV)
                        .withMisfireHandlingInstructionFireAndProceed())  // MISFIRE_INSTRUCTION_FIRE_ONCE_NOW
                .build();
    }

    @Bean
    public Trigger staleRepairDemoTrigger() {
        return TriggerBuilder.newTrigger()
                .forJob(STALE_REPAIR_JOB)
                .withIdentity(STALE_REPAIR_DEMO)
                .withDescription("Демо: кожні 2 хв, staleDays=0 перекриває 7 з JobDetail")
                .usingJobData(StaleRepairJob.KEY_STALE_DAYS, 0)          // перекриття значення з JobDetail
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0/2 * * * ?")
                        .inTimeZone(KYIV)
                        .withMisfireHandlingInstructionDoNothing())       // пропущені запуски ігноруємо
                .build();
    }

    // ---------------------------------------------------------------- JOB 2
    @Bean
    public JobDetail fleetSnapshotJobDetail() {
        return JobBuilder.newJob(FleetSnapshotJob.class)
                .withIdentity(FLEET_SNAPSHOT_JOB)
                .withDescription("Щоденний знімок стану автопарку")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger fleetSnapshotNightlyTrigger() {
        return TriggerBuilder.newTrigger()
                .forJob(FLEET_SNAPSHOT_JOB)
                .withIdentity(FLEET_SNAPSHOT_NIGHTLY)
                .withDescription("Щодня о 23:30 за Києвом")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 30 23 * * ?")
                        .inTimeZone(KYIV)
                        .withMisfireHandlingInstructionFireAndProceed())
                .build();
    }
}
