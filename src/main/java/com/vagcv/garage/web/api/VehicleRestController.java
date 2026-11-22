package com.vagcv.garage.web.api;

import com.vagcv.garage.dto.VehicleRequestDto;
import com.vagcv.garage.dto.VehicleResponseDto;
import com.vagcv.garage.service.VehicleService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/vehicles")
@RequiredArgsConstructor
public class VehicleRestController {

    private final VehicleService service;

    @GetMapping
    public List<VehicleResponseDto> list() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public VehicleResponseDto get(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VehicleResponseDto create(@Valid @RequestBody VehicleRequestDto request, Authentication auth) {
        return service.create(request, auth.getName());
    }

    @PutMapping("/{id}")
    public VehicleResponseDto update(@PathVariable Long id, @Valid @RequestBody VehicleRequestDto request,
                                     Authentication auth) {
        return service.update(id, request, auth.getName());
    }

    @PostMapping("/{id}/ready-for-sale")
    public VehicleResponseDto markReadyForSale(@PathVariable Long id, Authentication auth) {
        return service.markReadyForSale(id, auth.getName());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);     // @PreAuthorize("hasRole('ADMIN')") усередині сервісу
    }
}
