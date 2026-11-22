package com.vagcv.garage.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.quartz.CronExpression;
import org.quartz.CronTrigger;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.Trigger;

/** Перевіряє КОНФІГУРАЦІЮ jobs/triggers без БД і без запуску Spring: звичайні бінe-методи. */
class QuartzConfigTest {

    private final QuartzConfig config = new QuartzConfig();
    private final JobDetail staleJob = config.staleRepairJobDetail();
    private final JobDetail snapshotJob = config.fleetSnapshotJobDetail();
    private final Trigger daily = config.staleRepairDailyTrigger();
    private final Trigger demo = config.staleRepairDemoTrigger();
    private final Trigger nightly = config.fleetSnapshotNightlyTrigger();

    @Test
    void jobsHaveExplicitNamesAndGroups() {
        assertThat(staleJob.getKey().getName()).isEqualTo("stale-repair-reminder");
        assertThat(staleJob.getKey().getGroup()).isEqualTo("garage-maintenance");
        assertThat(snapshotJob.getKey().getName()).isEqualTo("fleet-snapshot");
        assertThat(snapshotJob.getKey().getGroup()).isEqualTo("garage-reporting");
        assertThat(staleJob.isDurable()).isTrue();
    }

    @Test
    void triggersPointToTheirJobsByJobKey() {
        assertThat(daily.getJobKey()).isEqualTo(staleJob.getKey());
        assertThat(demo.getJobKey()).isEqualTo(staleJob.getKey());       // один job - два тригери
        assertThat(nightly.getJobKey()).isEqualTo(snapshotJob.getKey());
        assertThat(daily.getKey()).isNotEqualTo(demo.getKey());
    }

    @Test
    void jobsAreMarkedDisallowConcurrentExecution() {
        assertThat(staleJob.isConcurrentExectionDisallowed()).isTrue();
        assertThat(snapshotJob.isConcurrentExectionDisallowed()).isTrue();
    }

    @Test
    void triggerJobDataOverridesJobDetailDefaults() {
        // Quartz будує змерджену мапу так: спочатку JobDetail, поверх - Trigger
        JobDataMap mergedDaily = merged(staleJob, daily);
        JobDataMap mergedDemo = merged(staleJob, demo);

        assertThat(staleJob.getJobDataMap().getInt(StaleRepairJob.KEY_STALE_DAYS)).isEqualTo(7);
        assertThat(mergedDaily.getInt(StaleRepairJob.KEY_STALE_DAYS)).isEqualTo(7);   // береться з job
        assertThat(mergedDemo.getInt(StaleRepairJob.KEY_STALE_DAYS)).isEqualTo(0);    // перекрито тригером
        assertThat(mergedDemo.getInt(StaleRepairJob.KEY_MAX_RETRIES)).isEqualTo(2);   // решта - з job
    }

    @Test
    void misfireInstructionsAreExplicit() {
        assertThat(daily.getMisfireInstruction()).isEqualTo(CronTrigger.MISFIRE_INSTRUCTION_FIRE_ONCE_NOW);
        assertThat(demo.getMisfireInstruction()).isEqualTo(CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING);
        assertThat(nightly.getMisfireInstruction()).isEqualTo(CronTrigger.MISFIRE_INSTRUCTION_FIRE_ONCE_NOW);
    }

    @Test
    void cronTriggersUseExplicitKyivTimeZone() {
        for (Trigger t : new Trigger[] {daily, demo, nightly}) {
            assertThat(((CronTrigger) t).getTimeZone().getID()).isEqualTo("Europe/Kyiv");
        }
    }

    @Test
    void dailyTriggerFiresAt0900KyivRegardlessOfServerZone() throws Exception {
        CronTrigger cron = (CronTrigger) daily;
        // CronExpression + часовий пояс з конфігурації тригера (startTime тригера = "зараз", тому рахуємо напряму)
        CronExpression expr = new CronExpression(cron.getCronExpression());
        expr.setTimeZone(cron.getTimeZone());
        ZoneId kyiv = ZoneId.of("Europe/Kyiv");

        // літо (UTC+3): 09:00 Kyiv = 06:00 UTC
        Date summer = expr.getNextValidTimeAfter(Date.from(Instant.parse("2026-07-01T00:00:00Z")));
        assertThat(summer.toInstant()).isEqualTo(Instant.parse("2026-07-01T06:00:00Z"));
        // зима (UTC+2): 09:00 Kyiv = 07:00 UTC
        Date winter = expr.getNextValidTimeAfter(Date.from(Instant.parse("2026-12-01T00:00:00Z")));
        assertThat(winter.toInstant()).isEqualTo(Instant.parse("2026-12-01T07:00:00Z"));
        // в обох випадках місцевий час = 09:00
        assertThat(ZonedDateTime.ofInstant(summer.toInstant(), kyiv).getHour()).isEqualTo(9);
        assertThat(ZonedDateTime.ofInstant(winter.toInstant(), kyiv).getHour()).isEqualTo(9);
    }

    private static JobDataMap merged(JobDetail job, Trigger trigger) {
        JobDataMap map = new JobDataMap();
        map.putAll(job.getJobDataMap());
        map.putAll(trigger.getJobDataMap());
        return map;
    }
}
