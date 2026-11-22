package com.vagcv.garage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Щоденний "знімок" стану автопарку. Унікальний на snapshot_date. */
@Entity
@Table(name = "fleet_snapshots")
@Getter
@Setter
@NoArgsConstructor
public class FleetSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "snapshot_date", nullable = false, unique = true)
    private LocalDate snapshotDate;

    @Column(name = "total_vehicles", nullable = false)
    private int totalVehicles;

    @Column(name = "in_repair", nullable = false)
    private int inRepair;

    @Column(name = "ready_for_sale", nullable = false)
    private int readyForSale;

    @Column(name = "sold", nullable = false)
    private int sold;

    @Column(name = "expected_profit", nullable = false)
    private BigDecimal expectedProfit;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
