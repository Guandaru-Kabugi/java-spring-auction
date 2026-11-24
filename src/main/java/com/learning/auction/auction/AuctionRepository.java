package com.learning.auction.auction;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

interface AuctionRepository extends JpaRepository<Auction, UUID> {

    /**
     * SELECT ... FOR UPDATE. Every bid on the same auction queues on this row lock, so bids are
     * applied strictly one after another. Pessimistic locking suits "hot" auctions: with
     * optimistic locking, 1000 simultaneous bidders would mean ~999 retries per round.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Auction a where a.id = :id")
    Optional<Auction> findByIdForUpdate(@Param("id") UUID id);

    /**
     * FOR UPDATE SKIP LOCKED: if a bid (or another app instance's scheduler) holds the row, skip
     * it this tick instead of waiting. The next tick retries - closure is idempotent.
     */
    @Query(nativeQuery = true, value = "SELECT * FROM auctions WHERE id = :id FOR UPDATE SKIP LOCKED")
    Optional<Auction> tryLockById(@Param("id") UUID id);

    @Query(nativeQuery = true, value = """
            SELECT id FROM auctions
             WHERE status = 'SCHEDULED' AND start_time <= :now
             ORDER BY start_time LIMIT :limit
            """)
    List<UUID> findDueToStart(@Param("now") Instant now, @Param("limit") int limit);

    @Query(nativeQuery = true, value = """
            SELECT id FROM auctions
             WHERE status = 'ACTIVE' AND end_time <= :now
             ORDER BY end_time LIMIT :limit
            """)
    List<UUID> findDueToEnd(@Param("now") Instant now, @Param("limit") int limit);

    Page<Auction> findByStatus(AuctionStatus status, Pageable pageable);

    Page<Auction> findBySellerId(UUID sellerId, Pageable pageable);
}
