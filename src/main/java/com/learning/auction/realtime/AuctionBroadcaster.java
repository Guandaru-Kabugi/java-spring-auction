package com.learning.auction.realtime;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.learning.auction.events.Topics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

/**
 * Kafka -> WebSocket bridge: the "WebSocket server fans out update to all connected clients" box
 * from the design.
 *
 * <h2>EXERCISE 2 - implement the two listener methods</h2>
 * Test that tells you when you're done: {@code AuctionBroadcastIT} (remove its {@code @Disabled}).
 * Do Exercise 1 (OutboxRelay) first - without it nothing ever reaches Kafka.
 *
 * <h3>Steps for each method</h3>
 * <ol>
 *   <li>Parse the JSON payload into the event record:
 *       {@code jsonMapper.readValue(payload, BidPlacedEvent.class)}.</li>
 *   <li>Map it to an {@link AuctionUpdateMessage} (type BID_PLACED / AUCTION_ENDED; for a bid,
 *       currentPrice = amount and highestBidderId = bidderId).</li>
 *   <li>Push it: {@code messaging.convertAndSend(AuctionUpdateMessage.destination(auctionId), message)}.
 *       Spring serializes the record to JSON for every subscriber of that destination.</li>
 * </ol>
 *
 * <h3>Why the odd groupId?</h3>
 * Consumers in the <b>same</b> group split a topic's partitions between them - each event is handled
 * by ONE instance. That's what you want for "charge the winner", but wrong here: a watcher connected
 * to instance B would miss bids consumed by instance A. A unique group per instance makes every
 * instance receive every event. The SpEL expression generates a random group id at startup.
 *
 * <h3>Stretch goals</h3>
 * <ul>
 *   <li>Notify the outbid user privately: {@code messaging.convertAndSendToUser(name, "/queue/notifications", ...)}.
 *       Hint: the STOMP principal's name is the JWT {@code sub}, but the event has our internal user id.
 *       How would you bridge the two? (This is Phase 4 material.)</li>
 *   <li>A malformed payload makes the listener throw. What does spring-kafka do then, and how would you
 *       configure a {@code DefaultErrorHandler} with a dead-letter topic?</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
class AuctionBroadcaster {

    static final String GROUP_ID = "auction-broadcaster-#{T(java.util.UUID).randomUUID()}";

    private final SimpMessagingTemplate messaging;
    private final JsonMapper jsonMapper;

    @KafkaListener(id = "bidPlacedBroadcaster", topics = Topics.BID_PLACED, groupId = GROUP_ID)
    void onBidPlaced(String payload) {
        // TODO(exercise 2)
    }

    @KafkaListener(id = "auctionEndedBroadcaster", topics = Topics.AUCTION_ENDED, groupId = GROUP_ID)
    void onAuctionEnded(String payload) {
        // TODO(exercise 2)
    }
}
