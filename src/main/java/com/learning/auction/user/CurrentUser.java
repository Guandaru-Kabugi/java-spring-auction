package com.learning.auction.user;

import java.util.UUID;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/** Resolves the caller's internal user id from the JWT on the current request. */
@Component
@RequiredArgsConstructor
public class CurrentUser {

    private final UserService userService;

    public UUID id() {
        return userService.provision(jwt()).getId();
    }

    public boolean isAdmin() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    private Jwt jwt() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token) {
            return token.getToken();
        }
        throw new IllegalStateException("No authenticated JWT on this request");
    }
}
