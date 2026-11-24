package com.learning.auction.bid;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface BidRepository extends JpaRepository<Bid, UUID> {

    Optional<Bid> findByBidderIdAndIdempotencyKey(UUID bidderId, String idempotencyKey);

    Page<Bid> findByAuctionId(UUID auctionId, Pageable pageable);

    Page<Bid> findByBidderId(UUID bidderId, Pageable pageable);
}
