package com.learning.auction.events;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.extern.slf4j.Slf4j;

/**
 * Phase 1 stand-in for the event bus: logs every domain event once its transaction commits.
 * AFTER_COMMIT matters - we must never announce a bid that was later rolled back.
 * Phase 2 replaces this with a transactional outbox relayed to Kafka.
 */
@Slf4j
@Component
class DomainEventLogger {

    @TransactionalEventListener
    void on(DomainEvent event) {
        log.info("[{}] {}", event.topic(), event);
    }
}
