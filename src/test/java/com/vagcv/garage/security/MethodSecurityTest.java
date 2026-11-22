package com.vagcv.garage.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.vagcv.garage.exception.NotFoundException;
import com.vagcv.garage.mapper.VehicleMapper;
import com.vagcv.garage.repository.VehicleRepository;
import com.vagcv.garage.service.VehicleService;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * Критерій 7 (ЛР 1): @PreAuthorize блокує виклик методу сервісу без ролі ADMIN.
 * Піднімаємо мінімальний Spring-контекст: тільки сервіс + method security, без БД і Keycloak.
 */
@SpringJUnitConfig(MethodSecurityTest.TestConfig.class)
class MethodSecurityTest {

    @Configuration
    @EnableMethodSecurity
    static class TestConfig {
        @Bean VehicleRepository vehicleRepository() { return mock(VehicleRepository.class); }
        @Bean VehicleMapper vehicleMapper() { return mock(VehicleMapper.class); }
        @Bean Clock clock() { return Clock.systemUTC(); }
        @Bean VehicleService vehicleService(VehicleRepository r, VehicleMapper m, Clock c) {
            return new VehicleService(r, m, c);
        }
    }

    @Autowired
    VehicleService service;

    @Test
    @WithMockUser(roles = "MECHANIC")
    void delete_asMechanic_isBlockedByPreAuthorize() {
        assertThrows(AccessDeniedException.class, () -> service.delete(1L));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_asAdmin_passesSecurityAndReachesBusinessLogic() {
        // Доступ дозволено -> виконалась бізнес-логіка, яка не знайшла авто в порожньому (mock) репозиторії
        assertThrows(NotFoundException.class, () -> service.delete(1L));
    }

    @Test
    void keycloakRoles_areReadFromFlatClaimAndRealmAccess() {
        Set<String> roles = KeycloakRoles.roles(Map.of(
                "roles", List.of("MECHANIC"),
                "realm_access", Map.of("roles", List.of("ADMIN"))));
        assertEquals(Set.of("MECHANIC", "ADMIN"), roles);
        assertEquals("ROLE_ADMIN", KeycloakRoles.toAuthorities(Map.of("roles", List.of("ADMIN"))).get(0).getAuthority());
    }
}
