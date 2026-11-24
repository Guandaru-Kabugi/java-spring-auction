package com.learning.auction.outbox;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

import com.learning.auction.events.Topics;

/**
 * Declares the topics; Spring's {@link KafkaAdmin} creates any that are missing at startup.
 * 3 partitions = up to 3 consumers in one group can share the load. Replication factor 1 is
 * only OK for a single local broker (production would use 3).
 */
@Configuration
class KafkaTopicsConfig {

    private static final int PARTITIONS = 3;

    @Bean
    KafkaAdmin.NewTopics auctionTopics() {
        return new KafkaAdmin.NewTopics(
                topic(Topics.USER_REGISTERED),
                topic(Topics.AUCTION_STARTED),
                topic(Topics.BID_PLACED),
                topic(Topics.AUCTION_ENDED),
                topic(Topics.AUCTION_CANCELLED),
                topic(Topics.PAYMENT_FAILED));
    }

    private static NewTopic topic(String name) {
        return TopicBuilder.name(name).partitions(PARTITIONS).replicas(1).build();
    }
}
