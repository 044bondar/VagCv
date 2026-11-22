package com.vagcv.garage.dto;

import com.vagcv.garage.domain.VehicleStatus;
import java.math.BigDecimal;
import java.time.Instant;

/** expectedProfit = targetResalePrice - purchasePrice (рахується в мапері). */
public record VehicleResponseDto(
        Long id,
        String vin,
        String brand,
        String model,
        int manufactureYear,
        int mileageKm,
        BigDecimal purchasePrice,
        BigDecimal targetResalePrice,
        BigDecimal expectedProfit,
        VehicleStatus status,
        String createdBy,
        Instant updatedAt) {
}
