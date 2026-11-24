package com.learning.auction.events;

/** Kafka topic names, taken from the "Sample Event Topics" in the system design. */
public final class Topics {

    public static final String USER_REGISTERED = "user.registered";
    public static final String AUCTION_STARTED = "auction.started";
    public static final String BID_PLACED = "bid.placed";
    public static final String AUCTION_ENDED = "auction.ended";
    public static final String AUCTION_CANCELLED = "auction.cancelled";
    /** Used from Phase 3 (Stripe). */
    public static final String PAYMENT_FAILED = "payment.failed";

    private Topics() {
    }
}
