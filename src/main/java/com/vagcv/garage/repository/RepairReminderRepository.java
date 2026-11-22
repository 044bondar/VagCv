package com.vagcv.garage.repository;

import com.vagcv.garage.domain.RepairReminder;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepairReminderRepository extends JpaRepository<RepairReminder, Long> {
    boolean existsByVehicleIdAndReminderDate(Long vehicleId, LocalDate reminderDate);

    List<RepairReminder> findAllByOrderByReminderDateDescVehicleIdAsc();
}
