package com.learning.auction.listing;

import java.time.Clock;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.learning.auction.common.exception.NotFoundException;

import lombok.RequiredArgsConstructor;

/** Public API of the listing module; other modules call this, never the repository. */
@Service
@RequiredArgsConstructor
public class ListingService {

    private final ListingRepository repository;
    private final Clock clock;

    @Transactional
    public Listing create(UUID sellerId, String title, String description,
                          ListingCategory category, ItemCondition condition) {
        return repository.save(new Listing(sellerId, title, description, category, condition, clock.instant()));
    }

    @Transactional(readOnly = true)
    public Listing get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Listing", id));
    }

    @Transactional(readOnly = true)
    public Page<Listing> search(ListingCategory category, Pageable pageable) {
        return category == null ? repository.findAll(pageable) : repository.findByCategory(category, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Listing> bySeller(UUID sellerId, Pageable pageable) {
        return repository.findBySellerId(sellerId, pageable);
    }
}
