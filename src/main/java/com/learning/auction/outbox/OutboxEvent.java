package com.learning.auction.outbox;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One row of the transactional outbox: a domain event waiting to be published to Kafka. */
@Entity
@Table(name = "outbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    /** Max length kept from an exception message, to stop one bad row bloating the table. */
    private static final int MAX_ERROR_LENGTH = 2_000;

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String topic;

    @Column(name = "event_key", nullable = false, length = 100)
    private String eventKey;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    /** The event serialized as JSON - exactly the bytes that will become the Kafka record value. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    OutboxEvent(UUID id, String topic, String eventKey, String eventType, String payload, Instant createdAt) {
        this.id = id;
        this.topic = topic;
        this.eventKey = eventKey;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public boolean isPublished() {
        return publishedAt != null;
    }

    /** Call after Kafka has acknowledged the record. */
    void markPublished(Instant now) {
        this.publishedAt = now;
        this.attempts++;
        this.lastError = null;
    }

    /** Call when sending failed; the row stays unpublished and is retried on the next poll. */
    void markFailed(Exception error) {
        this.attempts++;
        String message = String.valueOf(error.getMessage());
        this.lastError = message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message;
    }
}
