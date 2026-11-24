package com.learning.auction.user;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Local copy of a Keycloak user. Keycloak owns credentials and profile; we keep our own row
 * so that auctions, bids and payments can reference a stable internal id.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAccount {

    @Id
    private UUID id;

    @Column(name = "keycloak_id", nullable = false, unique = true, length = 64)
    private String keycloakId;

    @Column(nullable = false)
    private String username;

    private String email;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;
}
