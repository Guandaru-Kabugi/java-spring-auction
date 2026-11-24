-- Phase 2: transactional outbox.
-- Domain events are inserted here in the SAME transaction as the business change (e.g. the bid),
-- so "bid saved" and "event recorded" either both happen or neither does. A relay then publishes
-- unpublished rows to Kafka. Delivery is at-least-once: consumers must tolerate duplicates.

CREATE TABLE outbox_events (
    id            UUID PRIMARY KEY,           -- also sent as the Kafka "eventId" header, for consumer de-duplication
    topic         VARCHAR(100) NOT NULL,
    event_key     VARCHAR(100) NOT NULL,      -- Kafka record key (auction id, user id, ...)
    event_type    VARCHAR(100) NOT NULL,      -- e.g. BidPlacedEvent, so consumers know how to parse
    payload       JSONB        NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    published_at  TIMESTAMPTZ,                -- NULL = still waiting for the relay
    attempts      INTEGER      NOT NULL DEFAULT 0,
    last_error    TEXT
);

-- The relay's only query: oldest unpublished rows first. A partial index stays tiny.
CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;
