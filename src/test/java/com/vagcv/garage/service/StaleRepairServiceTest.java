package com.vagcv.garage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vagcv.garage.domain.RepairReminder;
import com.vagcv.garage.domain.Vehicle;
import com.vagcv.garage.domain.VehicleStatus;
import com.vagcv.garage.repository.RepairReminderRepository;
import com.vagcv.garage.repository.VehicleRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StaleRepairServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-22T10:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final VehicleRepository vehicles = mock(VehicleRepository.class);
    private final RepairReminderRepository reminders = mock(RepairReminderRepository.class);
    private final StaleRepairService service = new StaleRepairService(vehicles, reminders, clock);

    private Vehicle car(long id, Instant updatedAt) {
        Vehicle v = new Vehicle();
        v.setId(id);
        v.setStatus(VehicleStatus.IN_REPAIR);
        v.setUpdatedAt(updatedAt);
        return v;
    }

    @Test
    void createsReminderOnlyForStaleVehicles() {
        Vehicle stale = car(1, NOW.minusSeconds(10L * 24 * 3600));   // 10 днів тому
        Vehicle fresh = car(2, NOW.minusSeconds(2L * 24 * 3600));    // 2 дні тому
        when(vehicles.findByStatus(VehicleStatus.IN_REPAIR)).thenReturn(List.of(stale, fresh));

        int created = service.createReminders(7);

        assertThat(created).isEqualTo(1);
        ArgumentCaptor<RepairReminder> captor = ArgumentCaptor.forClass(RepairReminder.class);
        verify(reminders).save(captor.capture());
        assertThat(captor.getValue().getVehicleId()).isEqualTo(1L);
        assertThat(captor.getValue().getDaysInRepair()).isEqualTo(10);
        assertThat(captor.getValue().getReminderDate()).isEqualTo(LocalDate.of(2026, 11, 22));
    }

    @Test
    void runningTwiceIsIdempotent() {
        Vehicle stale = car(1, NOW.minusSeconds(10L * 24 * 3600));
        when(vehicles.findByStatus(VehicleStatus.IN_REPAIR)).thenReturn(List.of(stale));

        // імітуємо "БД": existsBy... повертає true після першого save
        Set<String> stored = new HashSet<>();
        when(reminders.existsByVehicleIdAndReminderDate(any(), any()))
                .thenAnswer(i -> stored.contains(i.getArgument(0) + "|" + i.getArgument(1)));
        when(reminders.save(any(RepairReminder.class))).thenAnswer(i -> {
            RepairReminder r = i.getArgument(0);
            stored.add(r.getVehicleId() + "|" + r.getReminderDate());
            return r;
        });

        int first = service.createReminders(7);
        int second = service.createReminders(7);

        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(0);                      // повторний запуск нічого не дублює
        verify(reminders, times(1)).save(any(RepairReminder.class));
    }

    @Test
    void businessDateIsKyivDateNotUtcDate() {
        // 22:30 UTC 22 листопада = 00:30 23 листопада за Києвом (UTC+2)
        Clock lateUtc = Clock.fixed(Instant.parse("2026-11-22T22:30:00Z"), ZoneOffset.UTC);
        StaleRepairService s = new StaleRepairService(vehicles, reminders, lateUtc);
        when(vehicles.findByStatus(VehicleStatus.IN_REPAIR)).thenReturn(List.of(car(1, null)));

        s.createReminders(0);

        ArgumentCaptor<RepairReminder> captor = ArgumentCaptor.forClass(RepairReminder.class);
        verify(reminders).save(captor.capture());
        assertThat(captor.getValue().getReminderDate()).isEqualTo(LocalDate.of(2026, 11, 23));
    }

    @Test
    void negativeStaleDaysIsRejected() {
        assertThatThrownBy(() -> service.createReminders(-1)).isInstanceOf(IllegalArgumentException.class);
        verify(reminders, never()).save(any());
    }
}
