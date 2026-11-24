package com.learning.auction.auction;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.learning.auction.config.AuctionProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The "Scheduler Service" from the design, implemented as a database-polling job:
 * every second, find auctions whose start/end time has passed and transition them.
 * <p>
 * Why polling and not one timer per auction? Timers live in memory and vanish on restart or
 * deploy; the database is the source of truth, so a missed tick is simply caught up on the
 * next one. Safe across multiple instances thanks to FOR UPDATE SKIP LOCKED.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionLifecycleScheduler {

    private final AuctionRepository repository;
    private final AuctionLifecycleService lifecycle;
    private final AuctionProperties properties;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${auction.scheduler.poll-interval:PT1S}")
    public void tick() {
        Instant now = clock.instant();
        int limit = properties.scheduler().batchSize();

        for (UUID id : repository.findDueToStart(now, limit)) {
            runSafely("start", id, () -> lifecycle.start(id));
        }
        for (UUID id : repository.findDueToEnd(now, limit)) {
            runSafely("close", id, () -> lifecycle.close(id));
        }
    }

    /** Retry failed closures: a failure is logged and the auction is picked up again next tick. */
    private void runSafely(String action, UUID auctionId, Runnable task) {
        try {
            task.run();
        } catch (RuntimeException ex) {
            log.error("Failed to {} auction {} - will retry on next tick", action, auctionId, ex);
        }
    }
}
