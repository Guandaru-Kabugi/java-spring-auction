package com.learning.auction.events;

import java.time.Instant;
import java.util.UUID;

public record AuctionStartedEvent(UUID auctionId, UUID sellerId, Instant endTime, Instant occurredAt)
        implements DomainEvent {

    @Override
    public String topic() {
        return Topics.AUCTION_STARTED;
    }

    @Override
    public String key() {
        return auctionId.toString();
    }
}
