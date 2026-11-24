package com.learning.auction.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakRealmRoleConverterTest {

    private final KeycloakRealmRoleConverter converter = new KeycloakRealmRoleConverter();

    @Test
    void mapsRealmRolesToSpringRoles() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("alice")
                .claim("realm_access", Map.of("roles", List.of("user", "admin")))
                .build();

        assertThat(converter.convert(jwt)).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    void noRealmAccessClaimMeansNoRoles() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("alice").build();

        assertThat(converter.convert(jwt)).isEmpty();
    }
}
