package com.learning.auction.outbox;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Oldest unpublished events first, locked for the caller's transaction.
     * SKIP LOCKED lets several app instances run the relay at once without sending a row twice
     * (each instance grabs a different batch) and without blocking each other.
     */
    @Query(nativeQuery = true, value = """
            SELECT * FROM outbox_events
             WHERE published_at IS NULL
             ORDER BY created_at
             LIMIT :limit
             FOR UPDATE SKIP LOCKED
            """)
    List<OutboxEvent> lockNextBatch(@Param("limit") int limit);

    long countByPublishedAtIsNull();
}
