package com.learning.auction.auction;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.learning.auction.events.AuctionEndedEvent;
import com.learning.auction.events.AuctionStartedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Starts and closes single auctions, each in its own transaction so one failure cannot
 * block the rest of the batch. Both operations are idempotent: running them twice, or on
 * two app instances at once, has the same effect as running them once.
 */
@Slf4j
@Service
@RequiredArgsConstructor
class AuctionLifecycleService {

    /** Closing later than this after end_time is logged as a missed timer. */
    private static final Duration LATE_CLOSE_THRESHOLD = Duration.ofSeconds(5);

    private final AuctionRepository repository;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional
    public void start(UUID auctionId) {
        repository.tryLockById(auctionId).ifPresent(auction -> {
            Instant now = clock.instant();
            if (auction.activate(now)) {
                log.info("Auction {} is now ACTIVE (ends {})", auctionId, auction.getEndTime());
                events.publishEvent(new AuctionStartedEvent(auctionId, auction.getSellerId(), auction.getEndTime(), now));
            }
        });
    }

    @Transactional
    public void close(UUID auctionId) {
        repository.tryLockById(auctionId).ifPresent(auction -> {
            Instant now = clock.instant();
            if (!auction.close(now)) {
                return; // a soft-close extension moved end_time, or it was already closed
            }
            Duration lateBy = Duration.between(auction.getEndTime(), now);
            if (lateBy.compareTo(LATE_CLOSE_THRESHOLD) > 0) {
                log.warn("Auction {} closed {} after its end time - scheduler is falling behind", auctionId, lateBy);
            }
            log.info("Auction {} ENDED with outcome {} (winner={}, price={})",
                    auctionId, auction.getOutcome(), auction.getWinnerId(), auction.getCurrentPrice());

            BigDecimal finalPrice = auction.getOutcome() == AuctionOutcome.SOLD ? auction.getCurrentPrice() : null;
            events.publishEvent(new AuctionEndedEvent(auctionId, auction.getSellerId(), auction.getOutcome().name(),
                    auction.getWinnerId(), finalPrice, auction.getCurrency(), now));
        });
    }
}
