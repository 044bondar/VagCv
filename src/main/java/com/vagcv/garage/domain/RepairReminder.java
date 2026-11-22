package com.vagcv.garage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Нагадування "авто надто довго в ремонті". Унікальне на (vehicle_id, reminder_date). */
@Entity
@Table(name = "repair_reminders")
@Getter
@Setter
@NoArgsConstructor
public class RepairReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vehicle_id", nullable = false)
    private Long vehicleId;

    @Column(name = "reminder_date", nullable = false)
    private LocalDate reminderDate;

    @Column(name = "days_in_repair", nullable = false)
    private int daysInRepair;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static RepairReminder of(Long vehicleId, LocalDate date, int daysInRepair, Instant createdAt) {
        RepairReminder r = new RepairReminder();
        r.vehicleId = vehicleId;
        r.reminderDate = date;
        r.daysInRepair = daysInRepair;
        r.createdAt = createdAt;
        return r;
    }
}
