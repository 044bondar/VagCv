package com.vagcv.garage.web.api;

import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Показує, ким токен автентифікований і які ролі отримав (зручно для скріншота JWT). */
@RestController
@RequestMapping("/api/me")
public class MeRestController {

    @GetMapping
    public Map<String, Object> me(Authentication auth) {
        List<String> authorities = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).sorted().toList();
        return Map.of("username", auth.getName(), "authorities", authorities);
    }
}
