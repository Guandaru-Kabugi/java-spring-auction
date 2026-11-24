package com.learning.auction.bid;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.learning.auction.auction.Auction;
import com.learning.auction.auction.AuctionService;
import com.learning.auction.common.exception.BusinessRuleException;
import com.learning.auction.config.AuctionProperties;
import com.learning.auction.events.BidPlacedEvent;

import lombok.RequiredArgsConstructor;

/**
 * The "Bid Service" from the design. Placing a bid is one short transaction:
 * <ol>
 *   <li>lock the auction row (serialises all bids on this auction),</li>
 *   <li>replay check for the Idempotency-Key (safe client retries),</li>
 *   <li>let the {@link Auction} aggregate validate and apply the bid,</li>
 *   <li>append the bid row and publish {@code bid.placed} after commit.</li>
 * </ol>
 * The lock is held only for a handful of milliseconds, which keeps bid latency sub-second
 * even when many users bid on the same auction.
 */
@Service
@RequiredArgsConstructor
public class BidService {

    private final BidRepository bidRepository;
    private final AuctionService auctionService;
    private final AuctionProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional
    public PlacedBid placeBid(UUID auctionId, UUID bidderId, BigDecimal amount, String idempotencyKey) {
        // Timestamp on arrival, before possibly waiting for the lock, so queueing doesn't count against the bidder.
        Instant receivedAt = clock.instant();
        Auction auction = auctionService.lockForUpdate(auctionId);

        // Checked *after* taking the lock: a concurrent retry with the same key is now guaranteed
        // to see the first attempt's committed bid instead of racing it.
        Optional<PlacedBid> replay = findReplay(auctionId, bidderId, amount, idempotencyKey, auction);
        if (replay.isPresent()) {
            return replay.get();
        }

        Auction.BidAcceptance acceptance = auction.acceptBid(bidderId, amount, receivedAt, properties.bidding());
        BigDecimal accepted = acceptance.acceptedAmount();
        Bid bid = bidRepository.save(new Bid(auctionId, bidderId, accepted, receivedAt, idempotencyKey));

        events.publishEvent(new BidPlacedEvent(bid.getId(), auctionId, bidderId, accepted,
                auction.minimumNextBid(), auction.getCurrency(),
                auction.getBidCount(), acceptance.previousHighestBidderId(), auction.getEndTime(), receivedAt));
        return new PlacedBid(bid, auction, acceptance.endTimeExtended(), false);
    }

    @Transactional(readOnly = true)
    public Page<Bid> history(UUID auctionId, Pageable pageable) {
        auctionService.get(auctionId); // 404 for unknown auctions rather than an empty page
        return bidRepository.findByAuctionId(auctionId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Bid> byBidder(UUID bidderId, Pageable pageable) {
        return bidRepository.findByBidderId(bidderId, pageable);
    }

    private Optional<PlacedBid> findReplay(UUID auctionId, UUID bidderId, BigDecimal amount,
                                           String idempotencyKey, Auction auction) {
        if (idempotencyKey == null) {
            return Optional.empty();
        }
        return bidRepository.findByBidderIdAndIdempotencyKey(bidderId, idempotencyKey).map(existing -> {
            if (!existing.getAuctionId().equals(auctionId) || existing.getAmount().compareTo(amount) != 0) {
                throw new BusinessRuleException("IDEMPOTENCY_KEY_REUSED",
                        "This Idempotency-Key was already used for a different bid");
            }
            return new PlacedBid(existing, auction, false, true);
        });
    }

    /** @param replayed true when this is a retry that returned the original bid instead of creating one */
    public record PlacedBid(Bid bid, Auction auction, boolean endTimeExtended, boolean replayed) {
    }
}
