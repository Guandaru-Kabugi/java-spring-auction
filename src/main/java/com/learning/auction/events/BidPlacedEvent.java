package com.learning.auction.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Carries everything a watcher's UI needs ("event-carried state transfer"), so consumers such as
 * the WebSocket broadcaster never have to query the database to render an update.
 *
 * @param previousHighestBidderId who just got outbid (null on the first bid) - drives the
 *                                "you've been outbid" notification in Phase 4
 * @param endTime                 the auction end, which may have moved because of soft close
 */
public record BidPlacedEvent(
        UUID bidId,
        UUID auctionId,
        UUID bidderId,
        BigDecimal amount,
        BigDecimal minimumNextBid,
        String currency,
        int bidCount,
        UUID previousHighestBidderId,
        Instant endTime,
        Instant occurredAt) implements DomainEvent {

    @Override
    public String topic() {
        return Topics.BID_PLACED;
    }

    @Override
    public String key() {
        return auctionId.toString();
    }
}
