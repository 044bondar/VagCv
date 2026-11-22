package com.vagcv.garage.mapper;

import com.vagcv.garage.domain.Vehicle;
import com.vagcv.garage.dto.VehicleRequestDto;
import com.vagcv.garage.dto.VehicleResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/** Entity <-> DTO (MapStruct генерує реалізацію під час компіляції). */
@Mapper(componentModel = "spring")
public interface VehicleMapper {

    @Mapping(target = "expectedProfit",
             expression = "java(vehicle.getTargetResalePrice().subtract(vehicle.getPurchasePrice()))")
    VehicleResponseDto toResponse(Vehicle vehicle);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Vehicle toEntity(VehicleRequestDto request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(VehicleRequestDto request, @MappingTarget Vehicle vehicle);

    /** Для форми редагування в UI. */
    VehicleRequestDto toRequest(VehicleResponseDto response);
}
