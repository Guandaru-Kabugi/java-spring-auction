package com.learning.auction.user;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.learning.auction.common.exception.NotFoundException;
import com.learning.auction.events.UserRegisteredEvent;

import lombok.RequiredArgsConstructor;

/**
 * "Just-in-time" provisioning: the first time a Keycloak user calls the API we create
 * their local {@link UserAccount}. Sign-up and login themselves happen in Keycloak.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserAccountRepository repository;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** Returns the local user for this token, creating it on first sight. Cheap read on the hot path. */
    @Transactional
    public UserAccount provision(Jwt jwt) {
        return repository.findByKeycloakId(jwt.getSubject()).orElseGet(() -> create(jwt));
    }

    /** Like {@link #provision} but also copies the latest profile claims from the token. */
    @Transactional
    public UserAccount syncProfile(Jwt jwt) {
        UserAccount existing = provision(jwt);
        repository.refreshProfile(jwt.getSubject(), username(jwt), jwt.getClaimAsString("email"),
                jwt.getClaimAsString("name"), clock.instant());
        return repository.findById(existing.getId()).orElseThrow();
    }

    @Transactional(readOnly = true)
    public UserAccount get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("User", id));
    }

    private UserAccount create(Jwt jwt) {
        Instant now = clock.instant();
        int inserted = repository.insertIfAbsent(UUID.randomUUID(), jwt.getSubject(), username(jwt),
                jwt.getClaimAsString("email"), jwt.getClaimAsString("name"), now);
        UserAccount user = repository.findByKeycloakId(jwt.getSubject()).orElseThrow();
        if (inserted == 1) {
            events.publishEvent(new UserRegisteredEvent(user.getId(), user.getUsername(), user.getEmail(), now));
        }
        return user;
    }

    private static String username(Jwt jwt) {
        String preferred = jwt.getClaimAsString("preferred_username");
        return preferred == null || preferred.isBlank() ? jwt.getSubject() : preferred;
    }
}
