package com.vagcv.garage.security;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Keycloak віддає ролі в claim "roles" (наш mapper) і/або в "realm_access.roles".
 * Spring Security чекає на authority виду ROLE_ADMIN, тому додаємо префікс.
 */
public final class KeycloakRoles {

    private KeycloakRoles() {
    }

    public static Set<String> roles(Map<String, Object> claims) {
        Set<String> result = new LinkedHashSet<>();
        addAll(result, claims.get("roles"));
        if (claims.get("realm_access") instanceof Map<?, ?> realmAccess) {
            addAll(result, realmAccess.get("roles"));
        }
        return result;
    }

    public static List<GrantedAuthority> toAuthorities(Map<String, Object> claims) {
        return roles(claims).stream()
                .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }

    private static void addAll(Set<String> target, Object value) {
        if (value instanceof Collection<?> collection) {
            collection.forEach(item -> target.add(String.valueOf(item)));
        }
    }
}
