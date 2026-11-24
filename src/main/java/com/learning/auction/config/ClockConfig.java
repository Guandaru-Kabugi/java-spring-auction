package com.learning.auction.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Time is injected rather than read with Instant.now() so that auction timing rules
 * (start, end, soft close) can be tested with a fixed clock.
 */
@Configuration
class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
