package com.learning.auction.auction;

/** How an ENDED auction finished. Only SOLD leads to a payment. */
public enum AuctionOutcome {
    SOLD, RESERVE_NOT_MET, NO_BIDS
}
