package com.vagcv.garage.dto;

import com.vagcv.garage.domain.VehicleStatus;
import java.math.BigDecimal;
import java.util.Map;

public record FleetSummaryDto(long totalVehicles,
                              Map<VehicleStatus, Long> byStatus,
                              BigDecimal totalExpectedProfit) {
}
