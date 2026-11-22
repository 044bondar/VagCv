package com.vagcv.garage.web.ui;

import com.vagcv.garage.service.VehicleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** /ui/admin/** закритий для не-ADMIN правилом у SecurityFilterChain (-> 403). */
@Controller
@RequestMapping("/ui/admin")
@RequiredArgsConstructor
public class AdminViewController {

    private final VehicleService service;

    @GetMapping("/summary")
    public String summary(Model model) {
        model.addAttribute("summary", service.summary());
        return "admin-summary";
    }
}
