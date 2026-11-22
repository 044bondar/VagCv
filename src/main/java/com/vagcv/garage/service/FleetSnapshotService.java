package com.vagcv.garage.service;

import com.vagcv.garage.domain.FleetSnapshot;
import com.vagcv.garage.domain.VehicleStatus;
import com.vagcv.garage.dto.FleetSummaryDto;
import com.vagcv.garage.repository.FleetSnapshotRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Щоденний знімок автопарку. Ідемпотентний: другий запуск за ту саму дату нічого не пише. */
@Service
@RequiredArgsConstructor
@Slf4j
public class FleetSnapshotService {

    private final VehicleService vehicleService;
    private final FleetSnapshotRepository snapshots;
    private final Clock clock;

    /** @return true - знімок створено; false - за сьогодні вже існує (пропущено) */
    @Transactional
    public boolean takeSnapshot() {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, StaleRepairService.BUSINESS_ZONE);
        if (snapshots.existsBySnapshotDate(today)) {
            log.info("Fleet snapshot for {} already exists - skipped (idempotent)", today);
            return false;
        }
        FleetSummaryDto summary = vehicleService.summary();
        FleetSnapshot snapshot = new FleetSnapshot();
        snapshot.setSnapshotDate(today);
        snapshot.setTotalVehicles((int) summary.totalVehicles());
        snapshot.setInRepair(summary.byStatus().getOrDefault(VehicleStatus.IN_REPAIR, 0L).intValue());
        snapshot.setReadyForSale(summary.byStatus().getOrDefault(VehicleStatus.READY_FOR_SALE, 0L).intValue());
        snapshot.setSold(summary.byStatus().getOrDefault(VehicleStatus.SOLD, 0L).intValue());
        snapshot.setExpectedProfit(summary.totalExpectedProfit());
        snapshot.setCreatedAt(now);
        snapshots.save(snapshot);
        log.info("Fleet snapshot for {} saved: total={}, expectedProfit={}", today,
                snapshot.getTotalVehicles(), snapshot.getExpectedProfit());
        return true;
    }
}
