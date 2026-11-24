package com.learning.auction.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.learning.auction.auction.Auction;
import com.learning.auction.bid.BidService;
import com.learning.auction.common.exception.BusinessRuleException;
import com.learning.auction.events.Topics;
import com.learning.auction.support.IntegrationTest;
import com.learning.auction.support.TestData;

/** The write side: events land in the outbox atomically with the business change. */
class OutboxWriterIT extends IntegrationTest {

    @Autowired TestData data;
    @Autowired BidService bidService;
    @Autowired OutboxRepository outbox;

    private List<OutboxEvent> eventsFor(UUID auctionId, String topic) {
        return outbox.findAll().stream()
                .filter(e -> e.getEventKey().equals(auctionId.toString()) && e.getTopic().equals(topic))
                .toList();
    }

    @Test
    void acceptedBidIsRecordedInTheOutbox() {
        Auction auction = data.auction(data.user("seller"), "10.00", null);
        UUID bidder = data.user("bidder");

        bidService.placeBid(auction.getId(), bidder, new BigDecimal("10.00"), null);

        List<OutboxEvent> events = eventsFor(auction.getId(), Topics.BID_PLACED);
        assertThat(events).singleElement().satisfies(e -> {
            assertThat(e.getEventType()).isEqualTo("BidPlacedEvent");
            assertThat(e.getPayload()).contains(bidder.toString()).contains("\"minimumNextBid\"");
            assertThat(e.isPublished()).isFalse();
        });
    }

    @Test
    void rejectedBidLeavesNoEvent() {
        Auction auction = data.auction(data.user("seller"), "10.00", null);

        assertThatThrownBy(() -> bidService.placeBid(auction.getId(), data.user("bidder"), new BigDecimal("1.00"), null))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(eventsFor(auction.getId(), Topics.BID_PLACED)).isEmpty();
    }

    @Test
    void auctionCreationRecordsAuctionStarted() {
        Auction auction = data.auction(data.user("seller"), "10.00", null);

        assertThat(eventsFor(auction.getId(), Topics.AUCTION_STARTED)).hasSize(1);
    }
}
