package com.vagcv.garage.web.api;

import com.vagcv.garage.dto.FleetSummaryDto;
import com.vagcv.garage.service.VehicleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Увесь /api/admin/** закритий для не-ADMIN правилом у SecurityFilterChain (-> 403). */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminRestController {

    private final VehicleService service;

    @GetMapping("/summary")
    public FleetSummaryDto summary() {
        return service.summary();
    }
}
