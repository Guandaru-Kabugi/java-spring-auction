package com.learning.auction.auction;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.learning.auction.bid.BidService;
import com.learning.auction.common.exception.BusinessRuleException;
import com.learning.auction.support.IntegrationTest;
import com.learning.auction.support.MutableClock;
import com.learning.auction.support.TestData;

/** End-to-end bidding against a real PostgreSQL: locking, idempotency and the lifecycle scheduler. */
class BiddingFlowIT extends IntegrationTest {

    @Autowired BidService bidService;
    @Autowired AuctionLifecycleScheduler scheduler;
    @Autowired AuctionRepository auctionRepository;
    @Autowired MutableClock clock;
    @Autowired TestData data;

    @Test
    void concurrentEqualBidsProduceExactlyOneWinner() throws Exception {
        UUID seller = data.user("seller");
        Auction auction = data.auction(seller, "100.00", null);

        int bidders = 20;
        List<UUID> bidderIds = new ArrayList<>();
        for (int i = 0; i < bidders; i++) {
            bidderIds.add(data.user("bidder" + i));
        }

        // Everybody fires the same opening bid at the same instant.
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(bidders)) {
            for (UUID bidder : bidderIds) {
                Callable<Boolean> attempt = () -> {
                    go.await();
                    try {
                        bidService.placeBid(auction.getId(), bidder, new BigDecimal("100.00"), null);
                        return true;
                    } catch (BusinessRuleException e) {
                        assertThat(e.getCode()).isEqualTo("BID_TOO_LOW");
                        return false;
                    }
                };
                results.add(pool.submit(attempt));
            }
            go.countDown();

            long accepted = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    accepted++;
                }
            }
            assertThat(accepted).as("the row lock lets exactly one 100.00 bid through").isEqualTo(1);
        }

        Auction reloaded = auctionRepository.findById(auction.getId()).orElseThrow();
        assertThat(reloaded.getBidCount()).isEqualTo(1);
        assertThat(reloaded.getCurrentPrice()).isEqualByComparingTo("100.00");
    }

    @Test
    void retriedBidWithSameIdempotencyKeyIsNotDuplicated() {
        UUID seller = data.user("seller");
        UUID bidder = data.user("bidder");
        Auction auction = data.auction(seller, "50.00", null);

        BidService.PlacedBid first = bidService.placeBid(auction.getId(), bidder, new BigDecimal("50.00"), "key-1");
        BidService.PlacedBid retry = bidService.placeBid(auction.getId(), bidder, new BigDecimal("50.00"), "key-1");

        assertThat(first.replayed()).isFalse();
        assertThat(retry.replayed()).isTrue();
        assertThat(retry.bid().getId()).isEqualTo(first.bid().getId());
        assertThat(auctionRepository.findById(auction.getId()).orElseThrow().getBidCount()).isEqualTo(1);
    }

    @Test
    void schedulerClosesAuctionAndPicksWinner() {
        UUID seller = data.user("seller");
        UUID alice = data.user("alice");
        UUID bob = data.user("bob");
        Auction auction = data.auction(seller, "10.00", "15.00");

        bidService.placeBid(auction.getId(), alice, new BigDecimal("10.00"), null);
        bidService.placeBid(auction.getId(), bob, new BigDecimal("20.00"), null);

        clock.advance(Duration.ofMinutes(30));
        scheduler.tick();
        assertThat(auctionRepository.findById(auction.getId()).orElseThrow().getStatus())
                .as("not due yet").isEqualTo(AuctionStatus.ACTIVE);

        clock.advance(Duration.ofMinutes(31));
        scheduler.tick();

        Auction closed = auctionRepository.findById(auction.getId()).orElseThrow();
        assertThat(closed.getStatus()).isEqualTo(AuctionStatus.ENDED);
        assertThat(closed.getOutcome()).isEqualTo(AuctionOutcome.SOLD);
        assertThat(closed.getWinnerId()).isEqualTo(bob);
    }
}
