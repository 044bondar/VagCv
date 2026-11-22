package com.vagcv.garage.service;

import com.vagcv.garage.domain.RepairReminder;
import com.vagcv.garage.domain.Vehicle;
import com.vagcv.garage.domain.VehicleStatus;
import com.vagcv.garage.repository.RepairReminderRepository;
import com.vagcv.garage.repository.VehicleRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Бізнес-логіка job-а "застарілий ремонт". Уся робота - в ОДНІЙ транзакції (межа транзакції тут,
 * а не в job-класі). Метод ІДЕМПОТЕНТНИЙ: повторний запуск в один і той самий день нічого не дублює
 * (перевірка existsBy... + унікальний constraint у БД як остання лінія захисту).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StaleRepairService {

    /** Бізнес-день рахуємо за київським часом, а не за часовим поясом сервера. */
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Kyiv");

    private final VehicleRepository vehicles;
    private final RepairReminderRepository reminders;
    private final Clock clock;

    /**
     * @param staleDays авто в статусі IN_REPAIR, яке не оновлювалось >= staleDays днів, вважається "застарілим"
     * @return скільки НОВИХ нагадувань створено (0 при повторному запуску)
     */
    @Transactional
    public int createReminders(int staleDays) {
        if (staleDays < 0) {
            throw new IllegalArgumentException("staleDays must be >= 0 but was " + staleDays);
        }
        Instant now = clock.instant();
        Instant threshold = now.minus(Duration.ofDays(staleDays));
        LocalDate today = LocalDate.ofInstant(now, BUSINESS_ZONE);

        int created = 0;
        for (Vehicle vehicle : vehicles.findByStatus(VehicleStatus.IN_REPAIR)) {
            Instant lastChange = vehicle.getUpdatedAt();
            boolean stale = lastChange == null || !lastChange.isAfter(threshold);
            if (!stale) {
                continue;
            }
            if (reminders.existsByVehicleIdAndReminderDate(vehicle.getId(), today)) {
                continue; // вже створено сьогодні - це і є ідемпотентність
            }
            int days = lastChange == null ? 0 : (int) Duration.between(lastChange, now).toDays();
            reminders.save(RepairReminder.of(vehicle.getId(), today, days, now));
            created++;
        }
        log.info("Stale repair check (staleDays={}): created {} new reminder(s) for {}", staleDays, created, today);
        return created;
    }
}
