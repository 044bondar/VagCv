package com.vagcv.garage.service;

import com.vagcv.garage.domain.Vehicle;
import com.vagcv.garage.domain.VehicleStatus;
import com.vagcv.garage.dto.FleetSummaryDto;
import com.vagcv.garage.dto.VehicleRequestDto;
import com.vagcv.garage.dto.VehicleResponseDto;
import com.vagcv.garage.exception.ConflictException;
import com.vagcv.garage.exception.NotFoundException;
import com.vagcv.garage.mapper.VehicleMapper;
import com.vagcv.garage.repository.VehicleRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VehicleService {

    private final VehicleRepository repository;
    private final VehicleMapper mapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<VehicleResponseDto> findAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public VehicleResponseDto findById(Long id) {
        return mapper.toResponse(load(id));
    }

    @Transactional
    public VehicleResponseDto create(VehicleRequestDto request, String operator) {
        if (repository.existsByVin(request.getVin())) {
            throw new ConflictException("Vehicle with VIN " + request.getVin() + " already exists");
        }
        Vehicle vehicle = mapper.toEntity(request);
        vehicle.setStatus(VehicleStatus.IN_REPAIR);          // нове авто завжди починає з ремонту
        touch(vehicle, operator);
        return mapper.toResponse(repository.save(vehicle));
    }

    @Transactional
    public VehicleResponseDto update(Long id, VehicleRequestDto request, String operator) {
        Vehicle vehicle = load(id);
        if (repository.existsByVinAndIdNot(request.getVin(), id)) {
            throw new ConflictException("Vehicle with VIN " + request.getVin() + " already exists");
        }
        mapper.updateEntity(request, vehicle);
        touch(vehicle, operator);
        return mapper.toResponse(repository.save(vehicle));
    }

    /** Перевести авто в статус "готове до продажу". Проданий (SOLD) повторно виставити не можна. */
    @Transactional
    public VehicleResponseDto markReadyForSale(Long id, String operator) {
        Vehicle vehicle = load(id);
        if (vehicle.getStatus() == VehicleStatus.SOLD) {
            throw new ConflictException("Vehicle " + id + " is already SOLD and cannot be listed again");
        }
        vehicle.setStatus(VehicleStatus.READY_FOR_SALE);
        touch(vehicle, operator);
        return mapper.toResponse(repository.save(vehicle));
    }

    /**
     * Видалення дозволене лише ADMIN. Перевірка на рівні методу (@PreAuthorize) діє
     * незалежно від правил у SecurityFilterChain: механік пройде фільтр /api/vehicles/**,
     * але виконання методу буде заблоковано (AccessDeniedException).
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new NotFoundException("Vehicle " + id + " not found");
        }
        repository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public FleetSummaryDto summary() {
        List<Vehicle> all = repository.findAll();
        Map<VehicleStatus, Long> byStatus = new EnumMap<>(VehicleStatus.class);
        for (VehicleStatus s : VehicleStatus.values()) {
            byStatus.put(s, 0L);
        }
        BigDecimal profit = BigDecimal.ZERO;
        for (Vehicle v : all) {
            byStatus.merge(v.getStatus(), 1L, Long::sum);
            profit = profit.add(v.getTargetResalePrice().subtract(v.getPurchasePrice()));
        }
        return new FleetSummaryDto(all.size(), byStatus, profit);
    }

    private Vehicle load(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Vehicle " + id + " not found"));
    }

    private void touch(Vehicle vehicle, String operator) {
        vehicle.setCreatedBy(operator);
        vehicle.setUpdatedAt(clock.instant());
    }
}
