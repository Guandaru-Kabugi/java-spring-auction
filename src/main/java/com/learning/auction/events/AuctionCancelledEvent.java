package com.learning.auction.events;

import java.time.Instant;
import java.util.UUID;

public record AuctionCancelledEvent(UUID auctionId, UUID cancelledBy, Instant occurredAt) implements DomainEvent {

    @Override
    public String topic() {
        return Topics.AUCTION_CANCELLED;
    }

    @Override
    public String key() {
        return auctionId.toString();
    }
}
