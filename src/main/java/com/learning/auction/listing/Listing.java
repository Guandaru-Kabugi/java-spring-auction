package com.learning.auction.listing;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** The item being sold. Pricing and timing live on the {@code Auction}, not here. */
@Entity
@Table(name = "listings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Listing {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ListingCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_condition", nullable = false, length = 40)
    private ItemCondition condition;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    Listing(UUID sellerId, String title, String description, ListingCategory category,
            ItemCondition condition, Instant createdAt) {
        this.sellerId = sellerId;
        this.title = title;
        this.description = description;
        this.category = category;
        this.condition = condition;
        this.createdAt = createdAt;
    }

    public boolean isOwnedBy(UUID userId) {
        return sellerId.equals(userId);
    }
}
