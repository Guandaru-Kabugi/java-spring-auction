package com.learning.auction.support;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.security.oauth2.jwt.Jwt;

import com.learning.auction.auction.Auction;
import com.learning.auction.auction.AuctionService;
import com.learning.auction.auction.AuctionService.CreateAuctionCommand;
import com.learning.auction.listing.ItemCondition;
import com.learning.auction.listing.ListingCategory;
import com.learning.auction.listing.ListingService;
import com.learning.auction.user.UserService;

import lombok.RequiredArgsConstructor;

/** Builds users, listings and auctions for integration tests. Every name is made unique per call. */
@TestComponent
@RequiredArgsConstructor
public class TestData {

    private final UserService userService;
    private final ListingService listingService;
    private final AuctionService auctionService;
    private final MutableClock clock;

    public UUID user(String name) {
        String unique = name + "-" + UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("kc-" + unique)
                .claim("preferred_username", unique).build();
        return userService.provision(jwt).getId();
    }

    /** An ACTIVE auction ending in one hour, min increment 1.00. */
    public Auction auction(UUID sellerId, String startingPrice, String reservePrice) {
        UUID listingId = listingService.create(sellerId, "PlayStation 5", "Barely used",
                ListingCategory.GAMING, ItemCondition.LIKE_NEW).getId();
        return auctionService.create(sellerId, new CreateAuctionCommand(listingId, "USD",
                new BigDecimal(startingPrice), reservePrice == null ? null : new BigDecimal(reservePrice),
                new BigDecimal("1.00"), null, clock.instant().plus(Duration.ofHours(1))));
    }
}
