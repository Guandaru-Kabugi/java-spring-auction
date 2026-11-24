package com.learning.auction.outbox;

import java.time.Clock;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.learning.auction.events.DomainEvent;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * Write side of the outbox. A plain (synchronous) {@code @EventListener} runs inside the
 * publisher's transaction, so the outbox row commits - or rolls back - together with the bid.
 * <p>
 * Why not send to Kafka right here? If we sent first and the DB transaction then rolled back,
 * consumers would hear about a bid that never happened. If we committed first and then the app
 * crashed before sending, the event would be lost. The outbox removes both failure modes.
 */
@Component
@RequiredArgsConstructor
class OutboxWriter {

    private final OutboxRepository repository;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    /** MANDATORY: an event published outside a transaction is a bug - fail loudly. */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    void on(DomainEvent event) {
        repository.save(new OutboxEvent(
                UUID.randomUUID(),
                event.topic(),
                event.key(),
                event.getClass().getSimpleName(),
                jsonMapper.writeValueAsString(event),
                clock.instant()));
    }
}
