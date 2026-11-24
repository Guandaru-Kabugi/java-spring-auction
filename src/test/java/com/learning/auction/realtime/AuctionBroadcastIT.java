package com.learning.auction.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.test.utils.ContainerTestUtils;

import com.learning.auction.auction.Auction;
import com.learning.auction.auction.AuctionLifecycleScheduler;
import com.learning.auction.bid.BidService;
import com.learning.auction.outbox.OutboxRelay;
import com.learning.auction.support.IntegrationTest;
import com.learning.auction.support.MutableClock;
import com.learning.auction.support.StompTestClient;
import com.learning.auction.support.TestData;

/**
 * EXERCISE 2 acceptance test: the full pipeline
 * <pre>REST/BidService -> outbox -> OutboxRelay -> Kafka -> AuctionBroadcaster -> STOMP -> browser</pre>
 * Remove {@code @Disabled} (needs Exercise 1 done too), then run:
 * <pre>./mvnw verify -Dit.test=AuctionBroadcastIT</pre>
 */
@Disabled("Exercise 2: remove this line once AuctionBroadcaster is implemented")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuctionBroadcastIT extends IntegrationTest {

    @LocalServerPort int port;
    @Autowired TestData data;
    @Autowired BidService bidService;
    @Autowired OutboxRelay relay;
    @Autowired AuctionLifecycleScheduler scheduler;
    @Autowired MutableClock clock;
    @Autowired KafkaListenerEndpointRegistry listeners;

    @BeforeEach
    void waitForKafkaListeners() {
        // With auto-offset-reset=latest semantics a listener only sees records produced after it has
        // been assigned partitions, so wait for that (3 partitions per topic) before producing.
        ContainerTestUtils.waitForAssignment(listeners.getListenerContainer("bidPlacedBroadcaster"), 3);
        ContainerTestUtils.waitForAssignment(listeners.getListenerContainer("auctionEndedBroadcaster"), 3);
    }

    @Test
    void watchersReceiveNewBidsInRealTime() throws Exception {
        Auction auction = data.auction(data.user("seller"), "10.00", null);
        UUID bidder = data.user("bidder");

        try (StompTestClient watcher = new StompTestClient(port, null)) { // anonymous watcher
            BlockingQueue<AuctionUpdateMessage> updates =
                    watcher.subscribe(AuctionUpdateMessage.destination(auction.getId()), AuctionUpdateMessage.class);
            Thread.sleep(300); // let the SUBSCRIBE frame reach the server

            bidService.placeBid(auction.getId(), bidder, new BigDecimal("12.50"), null);
            relay.publishPending();

            AuctionUpdateMessage update = updates.poll(15, TimeUnit.SECONDS);
            assertThat(update).as("no message pushed within 15s").isNotNull();
            assertThat(update.type()).isEqualTo(AuctionUpdateMessage.Type.BID_PLACED);
            assertThat(update.auctionId()).isEqualTo(auction.getId());
            assertThat(update.currentPrice()).isEqualByComparingTo("12.50");
            assertThat(update.minimumNextBid()).isEqualByComparingTo("13.50");
            assertThat(update.bidCount()).isEqualTo(1);
            assertThat(update.highestBidderId()).isEqualTo(bidder);
        }
    }

    @Test
    void watchersAreToldWhenTheAuctionEnds() throws Exception {
        Auction auction = data.auction(data.user("seller"), "10.00", null);
        UUID bidder = data.user("bidder");
        bidService.placeBid(auction.getId(), bidder, new BigDecimal("10.00"), null);

        try (StompTestClient watcher = new StompTestClient(port, null)) {
            BlockingQueue<AuctionUpdateMessage> updates =
                    watcher.subscribe(AuctionUpdateMessage.destination(auction.getId()), AuctionUpdateMessage.class);
            Thread.sleep(300);

            clock.advance(Duration.ofHours(2));
            scheduler.tick();
            relay.publishPending();

            AuctionUpdateMessage ended = null;
            for (AuctionUpdateMessage m; (m = updates.poll(15, TimeUnit.SECONDS)) != null; ) {
                if (m.type() == AuctionUpdateMessage.Type.AUCTION_ENDED) {
                    ended = m;
                    break;
                }
            }
            assertThat(ended).as("AUCTION_ENDED message").isNotNull();
            assertThat(ended.outcome()).isEqualTo("SOLD");
            assertThat(ended.winnerId()).isEqualTo(bidder);
        }
    }
}
