package com.learning.auction.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on @Scheduled jobs (the auction lifecycle poller). Integration tests set
 * {@code auction.scheduler.enabled=false} and drive the scheduler by hand for determinism.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "auction.scheduler.enabled", havingValue = "true", matchIfMissing = true)
class SchedulingConfig {
}
