package com.vagcv.garage.scheduling;

import com.vagcv.garage.dto.JobInfoDto;
import com.vagcv.garage.dto.TriggerInfoDto;
import com.vagcv.garage.exception.NotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.quartz.CronTrigger;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.stereotype.Service;

/** Тонка обгортка над Scheduler для admin REST API: перегляд jobs/triggers і ручний запуск. */
@Service
@RequiredArgsConstructor
public class SchedulerAdminService {

    private final Scheduler scheduler;

    public List<JobInfoDto> listJobs() {
        try {
            List<JobInfoDto> result = new ArrayList<>();
            for (String group : scheduler.getJobGroupNames()) {
                for (JobKey key : scheduler.getJobKeys(GroupMatcher.jobGroupEquals(group))) {
                    JobDetail detail = scheduler.getJobDetail(key);
                    List<TriggerInfoDto> triggers = new ArrayList<>();
                    for (Trigger t : scheduler.getTriggersOfJob(key)) {
                        triggers.add(toDto(t));
                    }
                    result.add(new JobInfoDto(key.getName(), key.getGroup(), detail.getJobClass().getSimpleName(),
                            detail.getDescription(), detail.isDurable(), detail.isConcurrentExectionDisallowed(),
                            toMap(detail.getJobDataMap()), triggers));
                }
            }
            return result;
        } catch (SchedulerException e) {
            throw new IllegalStateException("Cannot read scheduler state: " + e.getMessage(), e);
        }
    }

    /** Одноразовий негайний запуск job-а; overrides перекривають JobDataMap (так само, як це робить trigger). */
    public void runNow(String group, String name, Map<String, Object> overrides) {
        JobKey key = JobKey.jobKey(name, group);
        try {
            if (!scheduler.checkExists(key)) {
                throw new NotFoundException("Job " + group + "." + name + " not found");
            }
            scheduler.triggerJob(key, new JobDataMap(overrides));
        } catch (SchedulerException e) {
            throw new IllegalStateException("Cannot trigger job: " + e.getMessage(), e);
        }
    }

    private TriggerInfoDto toDto(Trigger t) throws SchedulerException {
        String cron = null;
        String tz = null;
        if (t instanceof CronTrigger ct) {
            cron = ct.getCronExpression();
            tz = ct.getTimeZone().getID();
        }
        return new TriggerInfoDto(t.getKey().getName(), t.getKey().getGroup(), t.getClass().getSimpleName(), cron, tz,
                misfireName(t), scheduler.getTriggerState(t.getKey()).name(),
                t.getPreviousFireTime() == null ? null : t.getPreviousFireTime().toInstant(),
                t.getNextFireTime() == null ? null : t.getNextFireTime().toInstant(),
                toMap(t.getJobDataMap()));
    }

    public static String misfireName(Trigger t) {
        int i = t.getMisfireInstruction();
        if (t instanceof CronTrigger) {
            return switch (i) {
                case Trigger.MISFIRE_INSTRUCTION_SMART_POLICY -> "SMART_POLICY";
                case Trigger.MISFIRE_INSTRUCTION_IGNORE_MISFIRE_POLICY -> "IGNORE_MISFIRE_POLICY";
                case CronTrigger.MISFIRE_INSTRUCTION_FIRE_ONCE_NOW -> "FIRE_ONCE_NOW";
                case CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING -> "DO_NOTHING";
                default -> "UNKNOWN(" + i + ")";
            };
        }
        return String.valueOf(i);
    }

    private static Map<String, Object> toMap(JobDataMap map) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String k : map.getKeys()) {
            out.put(k, map.get(k));
        }
        return out;
    }
}
