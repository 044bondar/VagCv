package com.vagcv.garage.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Автомобіль у гаражі: від викупу до перепродажу. Таблиця створюється Liquibase (001-create-vehicles). */
@Entity
@Table(name = "vehicles")
@Getter
@Setter
@NoArgsConstructor
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 17)
    private String vin;

    private String brand;
    private String model;
    private int manufactureYear;
    private int mileageKm;

    @Column(precision = 12, scale = 2)
    private BigDecimal purchasePrice;

    @Column(precision = 12, scale = 2)
    private BigDecimal targetResalePrice;

    @Enumerated(EnumType.STRING)
    private VehicleStatus status;

    /** Оператор (preferred_username з Keycloak), який вніс або змінив авто. */
    private String createdBy;

    private Instant updatedAt;
}
