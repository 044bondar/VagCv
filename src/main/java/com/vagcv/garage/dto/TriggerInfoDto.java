package com.vagcv.garage.dto;

import java.time.Instant;
import java.util.Map;

public record TriggerInfoDto(String name,
                             String group,
                             String type,
                             String cron,
                             String timeZone,
                             String misfireInstruction,
                             String state,
                             Instant previousFireTime,
                             Instant nextFireTime,
                             Map<String, Object> triggerData) {
}
