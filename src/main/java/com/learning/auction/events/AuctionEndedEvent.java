package com.learning.auction.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published once per auction when the scheduler closes it. In Phase 3 the payment
 * module listens for SOLD outcomes and creates a Stripe payment for the winner.
 */
public record AuctionEndedEvent(
        UUID auctionId,
        UUID sellerId,
        String outcome,
        UUID winnerId,
        BigDecimal finalPrice,
        String currency,
        Instant occurredAt) implements DomainEvent {

    @Override
    public String topic() {
        return Topics.AUCTION_ENDED;
    }

    @Override
    public String key() {
        return auctionId.toString();
    }
}
