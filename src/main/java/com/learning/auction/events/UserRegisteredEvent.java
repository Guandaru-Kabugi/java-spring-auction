package com.learning.auction.events;

import java.time.Instant;
import java.util.UUID;

public record UserRegisteredEvent(UUID userId, String username, String email, Instant occurredAt)
        implements DomainEvent {

    @Override
    public String topic() {
        return Topics.USER_REGISTERED;
    }

    @Override
    public String key() {
        return userId.toString();
    }
}
