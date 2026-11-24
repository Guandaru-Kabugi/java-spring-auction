package com.learning.auction.auction;

/**
 * Lifecycle from the design: scheduled -> active -> ended. Cancelled is the admin/seller escape hatch.
 */
public enum AuctionStatus {
    SCHEDULED, ACTIVE, ENDED, CANCELLED
}
