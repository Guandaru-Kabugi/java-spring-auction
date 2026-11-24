package com.learning.auction.listing;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface ListingRepository extends JpaRepository<Listing, UUID> {

    Page<Listing> findBySellerId(UUID sellerId, Pageable pageable);

    Page<Listing> findByCategory(ListingCategory category, Pageable pageable);
}
