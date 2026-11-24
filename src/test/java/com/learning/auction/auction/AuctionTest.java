package com.learning.auction.auction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.learning.auction.common.exception.BusinessRuleException;
import com.learning.auction.config.AuctionProperties;

/** Pure domain tests: no Spring, no database - the rules live on the aggregate. */
class AuctionTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final AuctionProperties.Bidding RULES =
            new AuctionProperties.Bidding(Duration.ofMinutes(2), Duration.ofMinutes(2));

    private final UUID seller = UUID.randomUUID();
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    private Auction activeAuction(BigDecimal reserve) {
        return new Auction(UUID.randomUUID(), seller, "USD", new BigDecimal("100.00"), reserve,
                new BigDecimal("5.00"), NOW, NOW.plus(Duration.ofHours(1)), NOW);
    }

    private static BigDecimal usd(String value) {
        return new BigDecimal(value);
    }

    @Nested
    class Creation {

        @Test
        void startsActiveWhenStartTimeIsNow() {
            assertThat(activeAuction(null).getStatus()).isEqualTo(AuctionStatus.ACTIVE);
        }

        @Test
        void isScheduledWhenStartTimeIsInTheFuture() {
            Auction auction = new Auction(UUID.randomUUID(), seller, "USD", usd("10"), null, usd("1"),
                    NOW.plusSeconds(60), NOW.plus(Duration.ofHours(1)), NOW);

            assertThat(auction.getStatus()).isEqualTo(AuctionStatus.SCHEDULED);
            assertThat(auction.activate(NOW)).isFalse();
            assertThat(auction.activate(NOW.plusSeconds(60))).isTrue();
            assertThat(auction.activate(NOW.plusSeconds(61))).as("idempotent").isFalse();
            assertThat(auction.getStatus()).isEqualTo(AuctionStatus.ACTIVE);
        }
    }

    @Nested
    class Bidding {

        @Test
        void firstBidMustMeetStartingPrice() {
            Auction auction = activeAuction(null);

            assertThatThrownBy(() -> auction.acceptBid(alice, usd("99.99"), NOW, RULES))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasFieldOrPropertyWithValue("code", "BID_TOO_LOW");

            auction.acceptBid(alice, usd("100.00"), NOW, RULES);
            assertThat(auction.getCurrentPrice()).isEqualByComparingTo("100.00");
            assertThat(auction.getHighestBidderId()).isEqualTo(alice);
            assertThat(auction.getBidCount()).isEqualTo(1);
        }

        @Test
        void laterBidsMustBeatCurrentPriceByTheIncrement() {
            Auction auction = activeAuction(null);
            auction.acceptBid(alice, usd("100.00"), NOW, RULES);

            assertThat(auction.minimumNextBid()).isEqualByComparingTo("105.00");
            assertThatThrownBy(() -> auction.acceptBid(bob, usd("104.99"), NOW, RULES))
                    .hasFieldOrPropertyWithValue("code", "BID_TOO_LOW");

            Auction.BidAcceptance result = auction.acceptBid(bob, usd("105.00"), NOW, RULES);
            assertThat(result.previousHighestBidderId()).as("alice was outbid").isEqualTo(alice);
        }

        @Test
        void equalBidsCannotTie() {
            Auction auction = activeAuction(null);
            auction.acceptBid(alice, usd("120.00"), NOW, RULES);

            assertThatThrownBy(() -> auction.acceptBid(bob, usd("120.00"), NOW, RULES))
                    .hasFieldOrPropertyWithValue("code", "BID_TOO_LOW");
        }

        @Test
        void sellerCannotBidOnOwnAuction() {
            assertThatThrownBy(() -> activeAuction(null).acceptBid(seller, usd("100"), NOW, RULES))
                    .hasFieldOrPropertyWithValue("code", "SELLER_CANNOT_BID");
        }

        @Test
        void highestBidderCannotOutbidThemselves() {
            Auction auction = activeAuction(null);
            auction.acceptBid(alice, usd("100"), NOW, RULES);

            assertThatThrownBy(() -> auction.acceptBid(alice, usd("200"), NOW, RULES))
                    .hasFieldOrPropertyWithValue("code", "ALREADY_HIGHEST_BIDDER");
        }

        @Test
        void rejectsMoreThanTwoDecimalPlaces() {
            assertThatThrownBy(() -> activeAuction(null).acceptBid(alice, usd("100.001"), NOW, RULES))
                    .hasFieldOrPropertyWithValue("code", "INVALID_AMOUNT");
        }

        @Test
        void rejectsBidsAtOrAfterEndTime() {
            Auction auction = activeAuction(null);

            assertThatThrownBy(() -> auction.acceptBid(alice, usd("100"), auction.getEndTime(), RULES))
                    .hasFieldOrPropertyWithValue("code", "AUCTION_ENDED");
        }

        @Test
        void rejectsBidsOnScheduledAuctions() {
            Auction auction = new Auction(UUID.randomUUID(), seller, "USD", usd("10"), null, usd("1"),
                    NOW.plusSeconds(60), NOW.plus(Duration.ofHours(1)), NOW);

            assertThatThrownBy(() -> auction.acceptBid(alice, usd("10"), NOW, RULES))
                    .hasFieldOrPropertyWithValue("code", "AUCTION_NOT_ACTIVE");
        }
    }

    @Nested
    class SoftClose {

        @Test
        void bidOutsideWindowDoesNotExtend() {
            Auction auction = activeAuction(null);
            Instant end = auction.getEndTime();

            Auction.BidAcceptance result = auction.acceptBid(alice, usd("100"), end.minus(Duration.ofMinutes(10)), RULES);

            assertThat(result.endTimeExtended()).isFalse();
            assertThat(auction.getEndTime()).isEqualTo(end);
        }

        @Test
        void lastSecondBidExtendsTheAuction() {
            Auction auction = activeAuction(null);
            Instant originalEnd = auction.getEndTime();
            Instant snipe = originalEnd.minusSeconds(1);

            Auction.BidAcceptance result = auction.acceptBid(alice, usd("100"), snipe, RULES);

            assertThat(result.endTimeExtended()).isTrue();
            assertThat(auction.getEndTime()).isEqualTo(snipe.plus(Duration.ofMinutes(2)));
            assertThat(auction.getOriginalEndTime()).isEqualTo(originalEnd);
        }
    }

    @Nested
    class Closing {

        @Test
        void doesNotCloseBeforeEndTime() {
            Auction auction = activeAuction(null);

            assertThat(auction.close(auction.getEndTime().minusMillis(1))).isFalse();
            assertThat(auction.getStatus()).isEqualTo(AuctionStatus.ACTIVE);
        }

        @Test
        void highestBidderWinsWhenReserveIsMet() {
            Auction auction = activeAuction(usd("150"));
            auction.acceptBid(alice, usd("100"), NOW, RULES);
            auction.acceptBid(bob, usd("150"), NOW, RULES);

            assertThat(auction.close(auction.getEndTime())).isTrue();

            assertThat(auction.getStatus()).isEqualTo(AuctionStatus.ENDED);
            assertThat(auction.getOutcome()).isEqualTo(AuctionOutcome.SOLD);
            assertThat(auction.getWinnerId()).isEqualTo(bob);
        }

        @Test
        void noWinnerWhenReserveIsNotMet() {
            Auction auction = activeAuction(usd("500"));
            auction.acceptBid(alice, usd("100"), NOW, RULES);

            auction.close(auction.getEndTime());

            assertThat(auction.getOutcome()).isEqualTo(AuctionOutcome.RESERVE_NOT_MET);
            assertThat(auction.getWinnerId()).isNull();
        }

        @Test
        void noBidsOutcome() {
            Auction auction = activeAuction(null);
            auction.close(auction.getEndTime());

            assertThat(auction.getOutcome()).isEqualTo(AuctionOutcome.NO_BIDS);
        }

        @Test
        void closingIsIdempotent() {
            Auction auction = activeAuction(null);
            Instant end = auction.getEndTime();

            assertThat(auction.close(end)).isTrue();
            assertThat(auction.close(end.plusSeconds(5))).isFalse();
            assertThat(auction.getClosedAt()).isEqualTo(end);
        }
    }

    @Nested
    class Cancelling {

        @Test
        void sellerCanCancelBeforeAnyBids() {
            Auction auction = activeAuction(null);
            auction.cancel(false);
            assertThat(auction.getStatus()).isEqualTo(AuctionStatus.CANCELLED);
        }

        @Test
        void onlyAdminCanCancelOnceThereAreBids() {
            Auction auction = activeAuction(null);
            auction.acceptBid(alice, usd("100"), NOW, RULES);

            assertThatThrownBy(() -> auction.cancel(false)).hasFieldOrPropertyWithValue("code", "AUCTION_HAS_BIDS");
            auction.cancel(true);
            assertThat(auction.getStatus()).isEqualTo(AuctionStatus.CANCELLED);
        }
    }
}
