package com.vagcv.garage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vagcv.garage.domain.FleetSnapshot;
import com.vagcv.garage.domain.VehicleStatus;
import com.vagcv.garage.dto.FleetSummaryDto;
import com.vagcv.garage.repository.FleetSnapshotRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FleetSnapshotServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-22T10:00:00Z"), ZoneOffset.UTC);
    private final VehicleService vehicleService = mock(VehicleService.class);
    private final FleetSnapshotRepository snapshots = mock(FleetSnapshotRepository.class);
    private final FleetSnapshotService service = new FleetSnapshotService(vehicleService, snapshots, clock);

    private FleetSummaryDto summary() {
        Map<VehicleStatus, Long> by = new EnumMap<>(VehicleStatus.class);
        by.put(VehicleStatus.IN_REPAIR, 1L);
        by.put(VehicleStatus.READY_FOR_SALE, 1L);
        by.put(VehicleStatus.SOLD, 1L);
        return new FleetSummaryDto(3, by, new BigDecimal("5400.00"));
    }

    @Test
    void createsSnapshotWhenNoneExistsForToday() {
        when(snapshots.existsBySnapshotDate(LocalDate.of(2026, 11, 22))).thenReturn(false);
        when(vehicleService.summary()).thenReturn(summary());

        boolean created = service.takeSnapshot();

        assertThat(created).isTrue();
        ArgumentCaptor<FleetSnapshot> captor = ArgumentCaptor.forClass(FleetSnapshot.class);
        verify(snapshots).save(captor.capture());
        assertThat(captor.getValue().getTotalVehicles()).isEqualTo(3);
        assertThat(captor.getValue().getInRepair()).isEqualTo(1);
        assertThat(captor.getValue().getExpectedProfit()).isEqualByComparingTo("5400.00");
    }

    @Test
    void secondRunOnSameDayIsSkipped_idempotent() {
        when(snapshots.existsBySnapshotDate(LocalDate.of(2026, 11, 22))).thenReturn(false, true);
        when(vehicleService.summary()).thenReturn(summary());

        assertThat(service.takeSnapshot()).isTrue();
        assertThat(service.takeSnapshot()).isFalse();

        verify(snapshots, times(1)).save(any(FleetSnapshot.class));
        verify(vehicleService, times(1)).summary();
        verify(snapshots, times(2)).existsBySnapshotDate(any());
    }

    @Test
    void doesNotReadFleetWhenSnapshotAlreadyExists() {
        when(snapshots.existsBySnapshotDate(any())).thenReturn(true);

        assertThat(service.takeSnapshot()).isFalse();

        verify(vehicleService, never()).summary();
    }
}
