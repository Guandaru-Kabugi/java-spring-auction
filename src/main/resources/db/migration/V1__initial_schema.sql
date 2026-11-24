-- Phase 1 schema: users, listings, auctions, bids.
-- Modules reference each other by id only (no cross-module foreign-key navigation in JPA),
-- but the database still enforces referential integrity.

CREATE TABLE users (
    id            UUID PRIMARY KEY,
    keycloak_id   VARCHAR(64)  NOT NULL UNIQUE,   -- the JWT "sub" claim
    username      VARCHAR(255) NOT NULL,
    email         VARCHAR(255),
    display_name  VARCHAR(255),
    created_at    TIMESTAMPTZ  NOT NULL,
    last_seen_at  TIMESTAMPTZ  NOT NULL
);

CREATE TABLE listings (
    id           UUID PRIMARY KEY,
    seller_id    UUID         NOT NULL REFERENCES users (id),
    title        VARCHAR(200) NOT NULL,
    description  TEXT,
    category     VARCHAR(40)  NOT NULL,
    item_condition VARCHAR(40) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_listings_seller ON listings (seller_id);

CREATE TABLE auctions (
    id                 UUID PRIMARY KEY,
    listing_id         UUID           NOT NULL REFERENCES listings (id),
    seller_id          UUID           NOT NULL REFERENCES users (id),
    currency           VARCHAR(3)     NOT NULL,
    starting_price     NUMERIC(19, 2) NOT NULL CHECK (starting_price > 0),
    reserve_price      NUMERIC(19, 2) CHECK (reserve_price >= starting_price),
    min_increment      NUMERIC(19, 2) NOT NULL CHECK (min_increment > 0),
    current_price      NUMERIC(19, 2),
    highest_bidder_id  UUID REFERENCES users (id),
    bid_count          INTEGER        NOT NULL DEFAULT 0,
    start_time         TIMESTAMPTZ    NOT NULL,
    end_time           TIMESTAMPTZ    NOT NULL,
    original_end_time  TIMESTAMPTZ    NOT NULL,
    status             VARCHAR(20)    NOT NULL,
    outcome            VARCHAR(20),
    winner_id          UUID REFERENCES users (id),
    closed_at          TIMESTAMPTZ,
    created_at         TIMESTAMPTZ    NOT NULL,
    version            BIGINT         NOT NULL,
    CHECK (end_time > start_time)
);

-- The scheduler scans these two "due" conditions every second, so index them.
CREATE INDEX idx_auctions_due_start ON auctions (start_time) WHERE status = 'SCHEDULED';
CREATE INDEX idx_auctions_due_end   ON auctions (end_time)   WHERE status = 'ACTIVE';
CREATE INDEX idx_auctions_seller    ON auctions (seller_id);

-- A listing can only be in one live auction at a time (it can be re-listed after it ends/cancels).
CREATE UNIQUE INDEX uq_auctions_live_listing ON auctions (listing_id)
    WHERE status IN ('SCHEDULED', 'ACTIVE');

CREATE TABLE bids (
    id               UUID PRIMARY KEY,
    auction_id       UUID           NOT NULL REFERENCES auctions (id),
    bidder_id        UUID           NOT NULL REFERENCES users (id),
    amount           NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    placed_at        TIMESTAMPTZ    NOT NULL,
    idempotency_key  VARCHAR(100),
    -- A retried request with the same key must never create a second bid.
    CONSTRAINT uq_bids_idempotency UNIQUE (bidder_id, idempotency_key)
);
CREATE INDEX idx_bids_auction_amount ON bids (auction_id, amount DESC);
CREATE INDEX idx_bids_bidder ON bids (bidder_id);
