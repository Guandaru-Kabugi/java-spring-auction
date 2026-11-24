package com.learning.auction.user;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByKeycloakId(String keycloakId);

    /**
     * Two parallel first requests from a brand-new user would race with find-then-save.
     * ON CONFLICT DO NOTHING makes provisioning atomic; the return value (1 = inserted,
     * 0 = already existed) tells us exactly once that this user is new.
     */
    @Modifying(clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            INSERT INTO users (id, keycloak_id, username, email, display_name, created_at, last_seen_at)
            VALUES (:id, :keycloakId, :username, :email, :displayName, :now, :now)
            ON CONFLICT (keycloak_id) DO NOTHING
            """)
    int insertIfAbsent(@Param("id") UUID id,
                       @Param("keycloakId") String keycloakId,
                       @Param("username") String username,
                       @Param("email") String email,
                       @Param("displayName") String displayName,
                       @Param("now") Instant now);

    @Modifying(clearAutomatically = true)
    @Query("""
            update UserAccount u
               set u.username = :username, u.email = :email, u.displayName = :displayName, u.lastSeenAt = :now
             where u.keycloakId = :keycloakId
            """)
    int refreshProfile(@Param("keycloakId") String keycloakId,
                       @Param("username") String username,
                       @Param("email") String email,
                       @Param("displayName") String displayName,
                       @Param("now") Instant now);
}
