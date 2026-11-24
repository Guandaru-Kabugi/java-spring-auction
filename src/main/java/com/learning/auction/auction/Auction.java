package com.learning.auction.auction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.learning.auction.common.exception.BusinessRuleException;
import com.learning.auction.config.AuctionProperties;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Aggregate root for one auction. All bidding and lifecycle rules live here, so services only
 * orchestrate (load + lock, call a method, save, publish an event) and the rules can be unit
 * tested without Spring or a database.
 * <p>
 * Callers must hold a row lock (see {@link AuctionRepository#findByIdForUpdate}) before calling
 * a mutating method - that is what makes concurrent bids conflict-free.
 */
@Entity
@Table(name = "auctions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Auction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "listing_id", nullable = false)
    private UUID listingId;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "starting_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal startingPrice;

    /** Hidden minimum the seller will accept. Never exposed to bidders, only whether it is met. */
    @Column(name = "reserve_price", precision = 19, scale = 2)
    private BigDecimal reservePrice;

    @Column(name = "min_increment", nullable = false, precision = 19, scale = 2)
    private BigDecimal minIncrement;

    @Column(name = "current_price", precision = 19, scale = 2)
    private BigDecimal currentPrice;

    @Column(name = "highest_bidder_id")
    private UUID highestBidderId;

    @Column(name = "bid_count", nullable = false)
    private int bidCount;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    /** Can move later than {@link #originalEndTime} because of soft-close extensions. */
    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Column(name = "original_end_time", nullable = false)
    private Instant originalEndTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuctionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AuctionOutcome outcome;

    @Column(name = "winner_id")
    private UUID winnerId;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Optimistic-lock version; also lets clients detect that the auction changed. */
    @Version
    @Column(nullable = false)
    private Long version;

    Auction(UUID listingId, UUID sellerId, String currency, BigDecimal startingPrice, BigDecimal reservePrice,
            BigDecimal minIncrement, Instant startTime, Instant endTime, Instant now) {
        this.listingId = Objects.requireNonNull(listingId);
        this.sellerId = Objects.requireNonNull(sellerId);
        this.currency = currency;
        this.startingPrice = money(startingPrice);
        this.reservePrice = reservePrice == null ? null : money(reservePrice);
        this.minIncrement = money(minIncrement);
        this.startTime = startTime;
        this.endTime = endTime;
        this.originalEndTime = endTime;
        this.createdAt = now;
        this.status = startTime.isAfter(now) ? AuctionStatus.SCHEDULED : AuctionStatus.ACTIVE;
    }

    // ------------------------------------------------------------------ bidding

    /** Lowest amount the next bid must reach: the starting price, or current price + increment. */
    public BigDecimal minimumNextBid() {
        return bidCount == 0 ? startingPrice : currentPrice.add(minIncrement);
    }

    /**
     * Validates and records a bid. Ties are impossible: bids are applied one at a time under the
     * row lock and each must strictly exceed the previous one by at least {@code minIncrement},
     * so the first bid to reach a given amount wins.
     */
    public BidAcceptance acceptBid(UUID bidderId, BigDecimal amount, Instant at, AuctionProperties.Bidding rules) {
        if (status != AuctionStatus.ACTIVE) {
            throw new BusinessRuleException("AUCTION_NOT_ACTIVE", "Auction is " + status);
        }
        if (at.isBefore(startTime)) {
            throw new BusinessRuleException("AUCTION_NOT_STARTED", "Auction starts at " + startTime);
        }
        if (!at.isBefore(endTime)) {
            throw new BusinessRuleException("AUCTION_ENDED", "Auction ended at " + endTime);
        }
        if (sellerId.equals(bidderId)) {
            throw new BusinessRuleException("SELLER_CANNOT_BID", "Sellers cannot bid on their own auction");
        }
        if (bidderId.equals(highestBidderId)) {
            throw new BusinessRuleException("ALREADY_HIGHEST_BIDDER", "You are already the highest bidder");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new BusinessRuleException("INVALID_AMOUNT", "Amounts can have at most 2 decimal places");
        }
        BigDecimal minimum = minimumNextBid();
        if (amount.compareTo(minimum) < 0) {
            throw new BusinessRuleException("BID_TOO_LOW", "Bid must be at least " + minimum + " " + currency);
        }

        UUID previousHighestBidder = highestBidderId;
        BigDecimal accepted = money(amount);
        currentPrice = accepted;
        highestBidderId = bidderId;
        bidCount++;

        // Anti-sniping "soft close": a late bid gives everyone else time to respond.
        boolean extended = false;
        if (Duration.between(at, endTime).compareTo(rules.softCloseWindow()) < 0) {
            Instant newEnd = at.plus(rules.softCloseExtension());
            if (newEnd.isAfter(endTime)) {
                endTime = newEnd;
                extended = true;
            }
        }
        return new BidAcceptance(accepted, previousHighestBidder, extended);
    }

    /** @param acceptedAmount the bid amount normalised to 2 decimal places */
    public record BidAcceptance(BigDecimal acceptedAmount, UUID previousHighestBidderId, boolean endTimeExtended) {
    }

    /** All money is stored and returned with exactly 2 decimals (100 -> 100.00). */
    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    // ------------------------------------------------------------------ lifecycle

    /** @return true if this call moved the auction to ACTIVE (idempotent: false if already done) */
    boolean activate(Instant now) {
        if (status != AuctionStatus.SCHEDULED || now.isBefore(startTime)) {
            return false;
        }
        status = AuctionStatus.ACTIVE;
        return true;
    }

    /** @return true if this call closed the auction (idempotent: false if not due or already closed) */
    boolean close(Instant now) {
        if (status != AuctionStatus.ACTIVE || now.isBefore(endTime)) {
            return false;
        }
        status = AuctionStatus.ENDED;
        closedAt = now;
        if (highestBidderId == null) {
            outcome = AuctionOutcome.NO_BIDS;
        } else if (isReserveMet()) {
            outcome = AuctionOutcome.SOLD;
            winnerId = highestBidderId;
        } else {
            outcome = AuctionOutcome.RESERVE_NOT_MET;
        }
        return true;
    }

    /**
     * Sellers may cancel before anyone has bid. Admins may cancel any live auction (fraud, disputes).
     */
    void cancel(boolean byAdmin) {
        if (status != AuctionStatus.SCHEDULED && status != AuctionStatus.ACTIVE) {
            throw new BusinessRuleException("AUCTION_NOT_CANCELLABLE", "Auction is already " + status);
        }
        if (bidCount > 0 && !byAdmin) {
            throw new BusinessRuleException("AUCTION_HAS_BIDS", "An auction with bids can only be cancelled by an admin");
        }
        status = AuctionStatus.CANCELLED;
    }

    public boolean hasReserve() {
        return reservePrice != null;
    }

    public boolean isReserveMet() {
        return reservePrice == null || (currentPrice != null && currentPrice.compareTo(reservePrice) >= 0);
    }
}
