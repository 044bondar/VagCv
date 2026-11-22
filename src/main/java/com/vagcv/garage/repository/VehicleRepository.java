package com.vagcv.garage.repository;

import com.vagcv.garage.domain.Vehicle;
import com.vagcv.garage.domain.VehicleStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    boolean existsByVin(String vin);

    boolean existsByVinAndIdNot(String vin, Long id);

    List<Vehicle> findByStatus(VehicleStatus status);
}
