package com.learning.auction.listing;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.learning.auction.common.web.PageResponse;
import com.learning.auction.user.CurrentUser;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
class ListingController {

    private final ListingService listingService;
    private final CurrentUser currentUser;

    @PostMapping("/api/listings")
    @ResponseStatus(HttpStatus.CREATED)
    ListingResponse create(@Valid @RequestBody CreateListingRequest request) {
        Listing listing = listingService.create(currentUser.id(), request.title(), request.description(),
                request.category(), request.condition());
        return ListingResponse.from(listing);
    }

    @GetMapping("/api/listings/{id}")
    ListingResponse get(@PathVariable UUID id) {
        return ListingResponse.from(listingService.get(id));
    }

    @GetMapping("/api/listings")
    PageResponse<ListingResponse> search(
            @RequestParam(required = false) ListingCategory category,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(listingService.search(category, pageable), ListingResponse::from);
    }

    @GetMapping("/api/users/me/listings")
    PageResponse<ListingResponse> mine(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(listingService.bySeller(currentUser.id(), pageable), ListingResponse::from);
    }

    record CreateListingRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 10_000) String description,
            @NotNull ListingCategory category,
            @NotNull ItemCondition condition) {
    }

    record ListingResponse(UUID id, UUID sellerId, String title, String description,
                           ListingCategory category, ItemCondition condition, Instant createdAt) {

        static ListingResponse from(Listing l) {
            return new ListingResponse(l.getId(), l.getSellerId(), l.getTitle(), l.getDescription(),
                    l.getCategory(), l.getCondition(), l.getCreatedAt());
        }
    }
}
