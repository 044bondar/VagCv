package com.vagcv.garage.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Вхідні дані для створення/оновлення авто (Bean Validation). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VehicleRequestDto {

    @NotBlank(message = "VIN is required")
    @Pattern(regexp = "[A-HJ-NPR-Z0-9]{17}",
             message = "VIN must be exactly 17 characters: digits and capital letters, without I, O, Q")
    private String vin;

    @NotBlank(message = "Brand is required")
    @Size(max = 50)
    private String brand;

    @NotBlank(message = "Model is required")
    @Size(max = 50)
    private String model;

    @Min(value = 1980, message = "Manufacture year must be 1980 or later")
    @Max(value = 2100, message = "Manufacture year is too large")
    private int manufactureYear;

    @Min(value = 0, message = "Mileage cannot be negative")
    private int mileageKm;

    @NotNull(message = "Purchase price is required")
    @DecimalMin(value = "0.01", message = "Purchase price must be positive")
    private BigDecimal purchasePrice;

    @NotNull(message = "Target resale price is required")
    @DecimalMin(value = "0.01", message = "Target resale price must be positive")
    private BigDecimal targetResalePrice;
}
