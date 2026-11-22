package com.vagcv.garage.dto;

import java.util.List;
import java.util.Map;

public record JobInfoDto(String name,
                         String group,
                         String jobClass,
                         String description,
                         boolean durable,
                         boolean concurrentExecutionDisallowed,
                         Map<String, Object> jobData,
                         List<TriggerInfoDto> triggers) {
}
