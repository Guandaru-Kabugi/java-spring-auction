package com.learning.auction.outbox;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Read side of the outbox: moves committed events from the {@code outbox_events} table to Kafka.
 *
 * <h2>EXERCISE 1 - implement {@link #publishPending()}</h2>
 * Test that tells you when you're done: {@code OutboxRelayIT} (remove its {@code @Disabled}).
 *
 * <h3>Steps</h3>
 * <ol>
 *   <li>Lock a batch: {@code repository.lockNextBatch(batchSize)}.</li>
 *   <li>For each row, build a {@code ProducerRecord<String, String>} with
 *       topic = {@code row.getTopic()}, key = {@code row.getEventKey()}, value = {@code row.getPayload()}.</li>
 *   <li>Add two headers so consumers can de-duplicate and parse without guessing:
 *       {@code "eventId"} = row id, {@code "eventType"} = row event type
 *       ({@code record.headers().add(name, bytes)} - use UTF-8 bytes).</li>
 *   <li>Send it with {@code kafkaTemplate.send(record)} and <b>wait</b> for the broker's ack:
 *       {@code .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)}. Waiting keeps rows in order and means
 *       we only mark a row published once Kafka really has it.</li>
 *   <li>Success: {@code row.markPublished(clock.instant())}.
 *       Failure: {@code row.markFailed(e)}, log a warning, and <b>stop the loop</b> ({@code break}) -
 *       if Kafka is down the next rows would fail too, and skipping ahead would break per-key ordering.</li>
 * </ol>
 * No explicit save needed: the rows are managed entities, so Hibernate flushes the changes on commit.
 *
 * <h3>Things to think about (answers in docs/phase-2-guide.md)</h3>
 * <ul>
 *   <li>The app crashes right after Kafka acks but before this transaction commits. What happens?
 *       Why is that acceptable? (keyword: at-least-once)</li>
 *   <li>Why does {@code InterruptedException} need special handling?</li>
 *   <li>Two app instances run this method at the same moment. Why don't they send the same row twice?</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    static final long SEND_TIMEOUT_SECONDS = 10;

    private final OutboxRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Clock clock;

    @Value("${outbox.relay.batch-size:100}")
    private int batchSize;

    /**
     * Runs every poll interval. Returns how many events were published (handy for tests and metrics).
     */
    @Scheduled(fixedDelayString = "${outbox.relay.poll-interval:PT0.5S}")
    @Transactional
    public int publishPending() {
        // TODO(exercise 1): implement the steps from the class Javadoc.
        return 0;
    }
}
