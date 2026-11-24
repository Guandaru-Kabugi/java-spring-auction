package com.learning.auction.user;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * The PDF's POST /signup and POST /login are handled by Keycloak (hosted login page with
 * self-registration enabled), so the API itself only exposes the current user's profile.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
class UserController {

    private final UserService userService;

    @GetMapping("/me")
    UserProfileResponse me(@AuthenticationPrincipal Jwt jwt, JwtAuthenticationToken authentication) {
        UserAccount user = userService.syncProfile(jwt);
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .toList();
        return new UserProfileResponse(user.getId(), user.getUsername(), user.getEmail(),
                user.getDisplayName(), roles, user.getCreatedAt());
    }

    record UserProfileResponse(UUID id, String username, String email, String displayName,
                               List<String> roles, Instant memberSince) {
    }
}
