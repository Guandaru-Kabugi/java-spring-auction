package com.learning.auction.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Tunable auction rules, bound from the {@code auction.*} section of application.yaml.
 */
@ConfigurationProperties("auction")
public record AuctionProperties(
        @DefaultValue("PT1M") Duration minDuration,
        @DefaultValue("P30D") Duration maxDuration,
        @DefaultValue Bidding bidding,
        @DefaultValue Scheduler scheduler) {

    /**
     * @param softCloseWindow    a bid placed this close to the end triggers an extension
     * @param softCloseExtension the auction then ends this long after that bid
     */
    public record Bidding(
            @DefaultValue("PT2M") Duration softCloseWindow,
            @DefaultValue("PT2M") Duration softCloseExtension) {
    }

    public record Scheduler(
            @DefaultValue("PT1S") Duration pollInterval,
            @DefaultValue("100") int batchSize) {
    }
}
