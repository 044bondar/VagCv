package com.vagcv.garage.web.ui;

import com.vagcv.garage.dto.VehicleRequestDto;
import com.vagcv.garage.exception.ConflictException;
import com.vagcv.garage.mapper.VehicleMapper;
import com.vagcv.garage.service.VehicleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Простий UI (Thymeleaf) для CRUD-сценаріїв. Оцінюється функціональність, а не дизайн. */
@Controller
@RequestMapping("/ui/vehicles")
@RequiredArgsConstructor
public class VehicleViewController {

    private final VehicleService service;
    private final VehicleMapper mapper;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("vehicles", service.findAll());
        return "vehicles";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        return form(model, new VehicleRequestDto(), "/ui/vehicles", "New vehicle");
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("vehicle") VehicleRequestDto vehicle, BindingResult errors,
                         Authentication auth, Model model) {
        if (!errors.hasErrors()) {
            try {
                service.create(vehicle, auth.getName());
                return "redirect:/ui/vehicles";
            } catch (ConflictException e) {
                errors.rejectValue("vin", "duplicate", e.getMessage());
            }
        }
        return form(model, vehicle, "/ui/vehicles", "New vehicle");
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        VehicleRequestDto dto = mapper.toRequest(service.findById(id));
        return form(model, dto, "/ui/vehicles/" + id, "Edit vehicle #" + id);
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("vehicle") VehicleRequestDto vehicle,
                         BindingResult errors, Authentication auth, Model model) {
        if (!errors.hasErrors()) {
            try {
                service.update(id, vehicle, auth.getName());
                return "redirect:/ui/vehicles";
            } catch (ConflictException e) {
                errors.rejectValue("vin", "duplicate", e.getMessage());
            }
        }
        return form(model, vehicle, "/ui/vehicles/" + id, "Edit vehicle #" + id);
    }

    @PostMapping("/{id}/ready-for-sale")
    public String markReady(@PathVariable Long id, Authentication auth, RedirectAttributes redirect) {
        try {
            service.markReadyForSale(id, auth.getName());
            redirect.addFlashAttribute("message", "Vehicle " + id + " is now READY_FOR_SALE");
        } catch (ConflictException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/ui/vehicles";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        service.delete(id);     // для MECHANIC: AccessDeniedException від @PreAuthorize -> сторінка 403
        redirect.addFlashAttribute("message", "Vehicle " + id + " deleted");
        return "redirect:/ui/vehicles";
    }

    private String form(Model model, VehicleRequestDto vehicle, String action, String title) {
        model.addAttribute("vehicle", vehicle);
        model.addAttribute("action", action);
        model.addAttribute("title", title);
        return "vehicle-form";
    }
}
