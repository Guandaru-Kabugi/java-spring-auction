package com.learning.auction.auction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.learning.auction.auction.AuctionService.CreateAuctionCommand;
import com.learning.auction.common.web.PageResponse;
import com.learning.auction.user.CurrentUser;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
class AuctionController {

    private final AuctionService auctionService;
    private final CurrentUser currentUser;

    @PostMapping("/api/auctions")
    @ResponseStatus(HttpStatus.CREATED)
    AuctionResponse create(@Valid @RequestBody CreateAuctionRequest request) {
        CreateAuctionCommand command = new CreateAuctionCommand(
                request.listingId(),
                request.currency() == null ? "USD" : request.currency(),
                request.startingPrice(),
                request.reservePrice(),
                request.minIncrement(),
                request.startTime(),
                request.endTime());
        return AuctionResponse.from(auctionService.create(currentUser.id(), command));
    }

    @GetMapping("/api/auctions/{id}")
    AuctionResponse get(@PathVariable UUID id) {
        return AuctionResponse.from(auctionService.get(id));
    }

    /** Ending-soonest first: the auctions where attention matters most. */
    @GetMapping("/api/auctions/active")
    PageResponse<AuctionResponse> active(
            @PageableDefault(size = 20, sort = "endTime", direction = Sort.Direction.ASC) Pageable pageable) {
        return PageResponse.of(auctionService.active(pageable), AuctionResponse::from);
    }

    @GetMapping("/api/users/me/auctions")
    PageResponse<AuctionResponse> mine(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(auctionService.bySeller(currentUser.id(), pageable), AuctionResponse::from);
    }

    @PostMapping("/api/auctions/{id}/cancel")
    AuctionResponse cancel(@PathVariable UUID id) {
        return AuctionResponse.from(auctionService.cancel(id, currentUser.id(), currentUser.isAdmin()));
    }

    record CreateAuctionRequest(
            @NotNull UUID listingId,
            @Pattern(regexp = "[A-Z]{3}", message = "must be an ISO-4217 code such as USD") String currency,
            @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal startingPrice,
            @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal reservePrice,
            @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal minIncrement,
            Instant startTime,
            @NotNull @Future Instant endTime) {
    }

    /** Note: the reserve price itself is never returned - only whether one exists and is met. */
    record AuctionResponse(
            UUID id,
            UUID listingId,
            UUID sellerId,
            AuctionStatus status,
            String currency,
            BigDecimal startingPrice,
            BigDecimal currentPrice,
            BigDecimal minimumNextBid,
            BigDecimal minIncrement,
            int bidCount,
            UUID highestBidderId,
            boolean hasReserve,
            boolean reserveMet,
            Instant startTime,
            Instant endTime,
            Instant originalEndTime,
            AuctionOutcome outcome,
            UUID winnerId,
            Instant closedAt,
            long version) {

        static AuctionResponse from(Auction a) {
            return new AuctionResponse(a.getId(), a.getListingId(), a.getSellerId(), a.getStatus(), a.getCurrency(),
                    a.getStartingPrice(), a.getCurrentPrice(), a.minimumNextBid(), a.getMinIncrement(),
                    a.getBidCount(), a.getHighestBidderId(), a.hasReserve(), a.isReserveMet(),
                    a.getStartTime(), a.getEndTime(), a.getOriginalEndTime(), a.getOutcome(), a.getWinnerId(),
                    a.getClosedAt(), a.getVersion() == null ? 0 : a.getVersion());
        }
    }
}
