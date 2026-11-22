package com.vagcv.garage.config;

import com.vagcv.garage.security.KeycloakRoles;
import java.util.HashSet;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Дві паралельні моделі автентифікації в одному застосунку:
 *  1) /api/**  -> JWT (Bearer-токен), без сесії (stateless)  - для Postman/curl
 *  2) решта    -> OIDC (Keycloak) + HTTP-сесія                - для користувачів UI
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // вмикає @PreAuthorize
public class SecurityConfig {

    // ---------- Ланцюжок №1: REST API з JWT ----------
    @Bean
    @Order(1)
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/**")
            .authorizeHttpRequests(auth -> auth
                // Спосіб 1 обмеження доступу: правило в SecurityFilterChain
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter())))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }

    // ---------- Ланцюжок №2: UI з OIDC (Keycloak) ----------
    @Bean
    @Order(2)
    SecurityFilterChain uiSecurity(HttpSecurity http, ClientRegistrationRepository clients) throws Exception {
        OidcClientInitiatedLogoutSuccessHandler logoutHandler = new OidcClientInitiatedLogoutSuccessHandler(clients);
        logoutHandler.setPostLogoutRedirectUri("{baseUrl}/");

        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/error").permitAll()
                .requestMatchers("/ui/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .oauth2Login(Customizer.withDefaults())            // редирект на сторінку логіну Keycloak
            .logout(logout -> logout.logoutSuccessHandler(logoutHandler));
        return http.build();
    }

    /** Ролі Keycloak з ID-токена -> ROLE_ADMIN / ROLE_MECHANIC для користувача UI. */
    @Bean
    GrantedAuthoritiesMapper oidcAuthoritiesMapper() {
        return authorities -> {
            Set<GrantedAuthority> mapped = new HashSet<>(authorities);
            for (GrantedAuthority authority : authorities) {
                if (authority instanceof OidcUserAuthority oidc) {
                    mapped.addAll(KeycloakRoles.toAuthorities(oidc.getAttributes()));
                }
            }
            return mapped;
        };
    }

    /** Ролі Keycloak з access-токена -> ROLE_* для JWT-автентифікації. */
    private JwtAuthenticationConverter jwtConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName("preferred_username");
        converter.setJwtGrantedAuthoritiesConverter(jwt -> KeycloakRoles.toAuthorities(jwt.getClaims()));
        return converter;
    }
}
