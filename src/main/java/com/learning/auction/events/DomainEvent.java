package com.learning.auction.events;

import java.time.Instant;

/**
 * Something that happened in the domain.
 * <p>
 * Flow since Phase 2: a service publishes the event in-process -> {@code OutboxWriter} stores it in
 * the {@code outbox_events} table inside the same DB transaction -> {@code OutboxRelay} sends it to
 * the Kafka topic {@link #topic()} using {@link #key()} as the record key.
 */
public sealed interface DomainEvent
        permits UserRegisteredEvent, AuctionStartedEvent, BidPlacedEvent, AuctionEndedEvent, AuctionCancelledEvent {

    /** Kafka topic, see {@link Topics}. */
    String topic();

    /**
     * Kafka record key. Kafka only guarantees ordering <i>within a partition</i>, and records with
     * the same key always go to the same partition - so keying by auction id means every consumer
     * sees one auction's bids in the order they were placed.
     */
    String key();

    Instant occurredAt();
}
