package com.learning.auction.auction;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.learning.auction.common.exception.BusinessRuleException;
import com.learning.auction.common.exception.ForbiddenOperationException;
import com.learning.auction.common.exception.NotFoundException;
import com.learning.auction.config.AuctionProperties;
import com.learning.auction.events.AuctionCancelledEvent;
import com.learning.auction.events.AuctionStartedEvent;
import com.learning.auction.listing.Listing;
import com.learning.auction.listing.ListingService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuctionService {

    /** Allow a little clock skew between client and server for "start now" requests. */
    private static final Duration START_TIME_TOLERANCE = Duration.ofMinutes(1);
    private static final BigDecimal DEFAULT_INCREMENT = new BigDecimal("1.00");

    private final AuctionRepository repository;
    private final ListingService listingService;
    private final AuctionProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional
    public Auction create(UUID sellerId, CreateAuctionCommand cmd) {
        Listing listing = listingService.get(cmd.listingId());
        if (!listing.isOwnedBy(sellerId)) {
            throw new ForbiddenOperationException("You can only auction your own listings");
        }

        Instant now = clock.instant();
        Instant start = cmd.startTime() == null ? now : cmd.startTime();
        if (start.isBefore(now.minus(START_TIME_TOLERANCE))) {
            throw new BusinessRuleException("START_IN_PAST", "Start time cannot be in the past");
        }
        Duration duration = Duration.between(start, cmd.endTime());
        if (duration.compareTo(properties.minDuration()) < 0 || duration.compareTo(properties.maxDuration()) > 0) {
            throw new BusinessRuleException("INVALID_DURATION",
                    "Auction must last between " + properties.minDuration() + " and " + properties.maxDuration());
        }
        if (cmd.reservePrice() != null && cmd.reservePrice().compareTo(cmd.startingPrice()) < 0) {
            throw new BusinessRuleException("INVALID_RESERVE", "Reserve price cannot be below the starting price");
        }

        Auction auction = repository.save(new Auction(listing.getId(), sellerId, cmd.currency(),
                cmd.startingPrice(), cmd.reservePrice(),
                cmd.minIncrement() == null ? DEFAULT_INCREMENT : cmd.minIncrement(),
                start, cmd.endTime(), now));

        if (auction.getStatus() == AuctionStatus.ACTIVE) {
            events.publishEvent(new AuctionStartedEvent(auction.getId(), sellerId, auction.getEndTime(), now));
        }
        return auction;
    }

    @Transactional(readOnly = true)
    public Auction get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Auction", id));
    }

    @Transactional(readOnly = true)
    public Page<Auction> active(Pageable pageable) {
        return repository.findByStatus(AuctionStatus.ACTIVE, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Auction> bySeller(UUID sellerId, Pageable pageable) {
        return repository.findBySellerId(sellerId, pageable);
    }

    /**
     * Loads the auction with a row lock for the caller's transaction (used by the bid module).
     * MANDATORY: a lock outside a transaction would be released immediately and protect nothing.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Auction lockForUpdate(UUID id) {
        return repository.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Auction", id));
    }

    @Transactional
    public Auction cancel(UUID auctionId, UUID userId, boolean isAdmin) {
        Auction auction = lockForUpdate(auctionId);
        if (!isAdmin && !auction.getSellerId().equals(userId)) {
            throw new ForbiddenOperationException("Only the seller or an admin can cancel this auction");
        }
        auction.cancel(isAdmin);
        events.publishEvent(new AuctionCancelledEvent(auctionId, userId, clock.instant()));
        return auction;
    }

    public record CreateAuctionCommand(UUID listingId, String currency, BigDecimal startingPrice,
                                       BigDecimal reservePrice, BigDecimal minIncrement,
                                       Instant startTime, Instant endTime) {
    }
}
