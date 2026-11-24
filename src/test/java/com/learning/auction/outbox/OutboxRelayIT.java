package com.learning.auction.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.ConsumerFactory;

import com.learning.auction.auction.Auction;
import com.learning.auction.bid.BidService;
import com.learning.auction.events.Topics;
import com.learning.auction.support.IntegrationTest;
import com.learning.auction.support.TestData;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * EXERCISE 1 acceptance test. Remove {@code @Disabled}, then run:
 * <pre>./mvnw verify -Dit.test=OutboxRelayIT</pre>
 */
@Disabled("Exercise 1: remove this line once OutboxRelay.publishPending() is implemented")
class OutboxRelayIT extends IntegrationTest {

    @Autowired TestData data;
    @Autowired BidService bidService;
    @Autowired OutboxRelay relay;
    @Autowired OutboxRepository outbox;
    @Autowired ConsumerFactory<String, String> consumerFactory;
    @Autowired JsonMapper jsonMapper;

    @Test
    void publishesBidToKafkaKeyedByAuctionWithHeaders() {
        Auction auction = data.auction(data.user("seller"), "10.00", null);
        bidService.placeBid(auction.getId(), data.user("bidder"), new BigDecimal("10.00"), null);
        OutboxEvent row = bidRow(auction.getId());

        assertThat(relay.publishPending()).as("number of events published").isPositive();

        ConsumerRecord<String, String> record = readFromKafka(Topics.BID_PLACED, auction.getId().toString());
        // Parse rather than string-match: PostgreSQL jsonb normalises whitespace and key order.
        JsonNode value = jsonMapper.readTree(record.value());
        assertThat(value.get("auctionId").asString()).isEqualTo(auction.getId().toString());
        assertThat(value.get("amount").decimalValue()).isEqualByComparingTo("10.00");
        assertThat(header(record, "eventId")).isEqualTo(row.getId().toString());
        assertThat(header(record, "eventType")).isEqualTo("BidPlacedEvent");

        assertThat(outbox.findById(row.getId()).orElseThrow().isPublished()).isTrue();
    }

    @Test
    void publishedRowsAreNotSentAgain() {
        Auction auction = data.auction(data.user("seller"), "10.00", null);
        bidService.placeBid(auction.getId(), data.user("bidder"), new BigDecimal("10.00"), null);

        relay.publishPending();
        assertThat(relay.publishPending()).as("second run has nothing left to send").isZero();
        assertThat(outbox.countByPublishedAtIsNull()).isZero();
    }

    // ------------------------------------------------------------------ helpers

    private OutboxEvent bidRow(UUID auctionId) {
        return outbox.findAll().stream()
                .filter(e -> e.getTopic().equals(Topics.BID_PLACED) && e.getEventKey().equals(auctionId.toString()))
                .findFirst().orElseThrow();
    }

    /** Reads the topic from the beginning with a throwaway consumer group until the key shows up. */
    private ConsumerRecord<String, String> readFromKafka(String topic, String key) {
        Properties props = new Properties();
        props.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (Consumer<String, String> consumer =
                     consumerFactory.createConsumer("relay-test-" + UUID.randomUUID(), null, null, props)) {
            consumer.subscribe(List.of(topic));
            List<ConsumerRecord<String, String>> seen = new ArrayList<>();
            long deadline = System.currentTimeMillis() + 15_000;
            while (System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> r : consumer.poll(Duration.ofMillis(500))) {
                    if (key.equals(r.key())) {
                        return r;
                    }
                    seen.add(r);
                }
            }
            throw new AssertionError("No record with key " + key + " on " + topic + " (saw " + seen.size() + " others)");
        }
    }

    private static String header(ConsumerRecord<?, ?> record, String name) {
        Header header = record.headers().lastHeader(name);
        assertThat(header).as("header '%s'", name).isNotNull();
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
