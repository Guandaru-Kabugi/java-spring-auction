package com.learning.auction.realtime;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * What watchers receive on {@code /topic/auctions/{auctionId}}. One shape for every update type,
 * so a frontend can handle them with a single switch on {@link #type()}. Fields that don't apply to
 * a type are null.
 */
public record AuctionUpdateMessage(
        Type type,
        UUID auctionId,
        BigDecimal currentPrice,
        BigDecimal minimumNextBid,
        String currency,
        Integer bidCount,
        UUID highestBidderId,
        Instant endTime,
        String outcome,
        UUID winnerId,
        Instant occurredAt) {

    public enum Type {
        BID_PLACED, AUCTION_ENDED
    }

    /** STOMP destination for one auction's watchers. */
    public static String destination(UUID auctionId) {
        return "/topic/auctions/" + auctionId;
    }
}
