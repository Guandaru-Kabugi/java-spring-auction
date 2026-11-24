package com.learning.auction.bid;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** An accepted bid. Bids are append-only: never updated or deleted, so the history is an audit log. */
@Entity
@Table(name = "bids")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Bid {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "auction_id", nullable = false)
    private UUID auctionId;

    @Column(name = "bidder_id", nullable = false)
    private UUID bidderId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "placed_at", nullable = false)
    private Instant placedAt;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    Bid(UUID auctionId, UUID bidderId, BigDecimal amount, Instant placedAt, String idempotencyKey) {
        this.auctionId = auctionId;
        this.bidderId = bidderId;
        this.amount = amount;
        this.placedAt = placedAt;
        this.idempotencyKey = idempotencyKey;
    }
}
