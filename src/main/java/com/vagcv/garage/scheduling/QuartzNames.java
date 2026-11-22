package com.vagcv.garage.scheduling;

import org.quartz.JobKey;
import org.quartz.TriggerKey;

/** Усі імена/групи jobs і triggers в одному місці (явні, а не згенеровані). */
public final class QuartzNames {

    public static final String GROUP_MAINTENANCE = "garage-maintenance";
    public static final String GROUP_REPORTING = "garage-reporting";

    public static final JobKey STALE_REPAIR_JOB = JobKey.jobKey("stale-repair-reminder", GROUP_MAINTENANCE);
    public static final TriggerKey STALE_REPAIR_DAILY = TriggerKey.triggerKey("stale-repair-daily", GROUP_MAINTENANCE);
    public static final TriggerKey STALE_REPAIR_DEMO = TriggerKey.triggerKey("stale-repair-demo-2min", GROUP_MAINTENANCE);

    public static final JobKey FLEET_SNAPSHOT_JOB = JobKey.jobKey("fleet-snapshot", GROUP_REPORTING);
    public static final TriggerKey FLEET_SNAPSHOT_NIGHTLY = TriggerKey.triggerKey("fleet-snapshot-nightly", GROUP_REPORTING);

    private QuartzNames() {
    }
}
