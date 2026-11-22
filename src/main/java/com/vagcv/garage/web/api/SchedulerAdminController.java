package com.vagcv.garage.web.api;

import com.vagcv.garage.domain.FleetSnapshot;
import com.vagcv.garage.domain.RepairReminder;
import com.vagcv.garage.dto.JobInfoDto;
import com.vagcv.garage.repository.FleetSnapshotRepository;
import com.vagcv.garage.repository.RepairReminderRepository;
import com.vagcv.garage.scheduling.SchedulerAdminService;
import com.vagcv.garage.scheduling.StaleRepairJob;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Керування планувальником (ЛР 6). /api/admin/** закрито для не-ADMIN у SecurityFilterChain. */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class SchedulerAdminController {

    private final SchedulerAdminService scheduler;
    private final RepairReminderRepository reminders;
    private final FleetSnapshotRepository snapshots;

    @GetMapping("/scheduler/jobs")
    public List<JobInfoDto> jobs() {
        return scheduler.listJobs();
    }

    /** Негайний одноразовий запуск. Необов'язкові параметри перекривають JobDataMap лише для цього запуску. */
    @PostMapping("/scheduler/jobs/{group}/{name}/run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> run(@PathVariable String group,
                                   @PathVariable String name,
                                   @RequestParam(required = false) Integer staleDays,
                                   @RequestParam(required = false) Boolean simulateFailure) {
        Map<String, Object> overrides = new LinkedHashMap<>();
        if (staleDays != null) {
            overrides.put(StaleRepairJob.KEY_STALE_DAYS, staleDays);
        }
        if (simulateFailure != null) {
            overrides.put(StaleRepairJob.KEY_SIMULATE_FAILURE, simulateFailure);
        }
        scheduler.runNow(group, name, overrides);
        return Map.of("status", "triggered", "job", group + "." + name, "overrides", overrides);
    }

    @GetMapping("/reminders")
    public List<RepairReminder> reminders() {
        return reminders.findAllByOrderByReminderDateDescVehicleIdAsc();
    }

    @GetMapping("/snapshots")
    public List<FleetSnapshot> snapshots() {
        return snapshots.findAllByOrderBySnapshotDateDesc();
    }
}
