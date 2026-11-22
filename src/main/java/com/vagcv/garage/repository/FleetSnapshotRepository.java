package com.vagcv.garage.repository;

import com.vagcv.garage.domain.FleetSnapshot;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FleetSnapshotRepository extends JpaRepository<FleetSnapshot, Long> {
    boolean existsBySnapshotDate(LocalDate snapshotDate);

    List<FleetSnapshot> findAllByOrderBySnapshotDateDesc();
}
