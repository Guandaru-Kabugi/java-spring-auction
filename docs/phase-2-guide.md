# Phase 2 guide: Kafka + WebSockets

Goal: when someone bids, every browser watching that auction updates within about a second, even
with several app instances behind a load balancer and even if Kafka goes down for a while.

```
 POST /bids ─► BidService ──(same DB transaction)──► bids row + outbox_events row
                                                              │
                                  OutboxRelay (every 0.5s)  ◄─┘   EXERCISE 1
                                              │
                                              ▼
                                  Kafka topic "bid.placed"  (key = auctionId)
                                              │  every app instance consumes it
                                              ▼
                                  AuctionBroadcaster        EXERCISE 2
                                              │
                                              ▼
                     STOMP /topic/auctions/{id} ─► browsers watching that auction
```

## What's already built (read these first)

| File | What to learn from it |
|---|---|
| `events/DomainEvent.java` | `topic()` + `key()`: why the key decides ordering |
| `outbox/OutboxWriter.java` | Why events go into a table instead of straight to Kafka |
| `db/migration/V2__outbox.sql` | Outbox schema, partial index |
| `outbox/OutboxRepository.java` | `FOR UPDATE SKIP LOCKED` again, now for the relay |
| `outbox/KafkaTopicsConfig.java` | Declaring topics and partitions |
| `realtime/WebSocketConfig.java` | STOMP endpoint, simple broker, destinations |
| `realtime/StompAuthInterceptor.java` | JWT auth on the CONNECT frame, per-destination rules |
| `application.yaml` (`spring.kafka`) | `acks=all`, idempotent producer, String serializers |

## Setup

```bash
docker compose up -d     # now also starts Kafka (:9092) and Kafka UI (http://localhost:8085)
./mvnw spring-boot:run
```

Open **http://localhost:8085** (Kafka UI) and look at the topics. They're empty until you finish Exercise 1.

## Exercise 1: OutboxRelay (database → Kafka)

**File:** `src/main/java/com/learning/auction/outbox/OutboxRelay.java`. The steps are in its Javadoc.
**Done when:** you remove `@Disabled` from `OutboxRelayIT` and this passes:

```bash
./mvnw verify -Dit.test=OutboxRelayIT
```

Useful APIs: `new ProducerRecord<>(topic, key, value)`, `record.headers().add(name, bytes)`,
`kafkaTemplate.send(record).get(timeout, unit)`.

**Try it live:** place a bid (`http/auction.http`), then in Kafka UI open `bid.placed`, then Messages. Check the
key, headers and partition. Place bids on two different auctions: same auction means the same partition,
every time.

**Break it on purpose:** `docker compose stop kafka`, place a few bids, watch `attempts` and `last_error`
grow in `outbox_events`, then `docker compose start kafka`. Nothing is lost.

## Exercise 2: AuctionBroadcaster (Kafka → WebSocket)

**File:** `src/main/java/com/learning/auction/realtime/AuctionBroadcaster.java`
**Done when:** you remove `@Disabled` from `AuctionBroadcastIT` and this passes:

```bash
./mvnw verify -Dit.test=AuctionBroadcastIT
```

**Try it live:** open http://localhost:8080/live.html in **two** browser tabs and paste the same auction id.
Bid as bob, then as carol, and both tabs update. Then start a second app instance on another port
(`./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8081`), open `localhost:8081/live.html`,
and bid through port 8080. The 8081 tab updates too. That's why each instance gets its own consumer group.

## Concepts to be able to explain afterwards

1. **Dual-write problem.** Why "save to DB, then send to Kafka" is broken in both orders.
2. **At-least-once delivery.** Where duplicates come from, and why consumers dedupe by `eventId`.
3. **Partitions and keys.** Ordering is per partition, so key = auctionId gives ordered bids per auction.
4. **Consumer groups.** Same group = share the work (each event handled once). Different groups = everyone gets everything.
5. **WebSocket auth.** Why it happens on the STOMP CONNECT frame and not the HTTP handshake.
6. **Event-carried state transfer.** Why `BidPlacedEvent` includes `minimumNextBid` and `endTime`.

<details>
<summary><b>Answers to the questions in OutboxRelay's Javadoc</b> (try them yourself first)</summary>

**Crash after the Kafka ack but before the DB commit?** The transaction rolls back, so the row stays
unpublished, and the next run sends it **again**. Kafka now has a duplicate. That's *at-least-once*
delivery. It's acceptable because the alternative (at-most-once) loses events, and duplicates are easy to
handle: every record carries `eventId`, so a consumer that must not double-process (e.g. "charge the winner"
in Phase 3) records the ids it has processed and skips repeats. For the broadcaster, a duplicate just pushes
the same price twice, which is harmless.

**InterruptedException?** `Future.get()` throws it when the thread is being shut down. Catching it clears the
thread's interrupt flag, so call `Thread.currentThread().interrupt()` to restore it. Otherwise the app's
shutdown can hang waiting for a thread that "forgot" it was asked to stop.

**Two instances at once?** `lockNextBatch` uses `FOR UPDATE SKIP LOCKED`. Instance A locks rows 1-100;
instance B's query skips those locked rows and takes 101-200 (or nothing). The locks are held until each
transaction commits, by which time the rows are marked published.

**Why `break` on failure instead of `continue`?** If row 5 (auction X) fails and you continue to row 6
(also auction X), consumers would see bid 6 before bid 5, which breaks per-auction ordering. And if Kafka
is down, the remaining sends would fail anyway.

**Why does the JSON in Kafka look reformatted?** The `payload` column is `jsonb`, and PostgreSQL stores JSON in
a parsed binary form. When it's read back, keys are reordered and whitespace normalised. The content is identical.
</details>

## Stretch goals

- Dead-letter topic: configure a `DefaultErrorHandler` with a `DeadLetterPublishingRecoverer`, then publish a
  malformed record from Kafka UI and watch it land in `bid.placed.DLT`.
- Outbox housekeeping: a scheduled job that deletes published rows older than 7 days.
- Micrometer gauge `outbox.pending` (= `countByPublishedAtIsNull()`). It's the single best health signal for this pipeline.
- Private "you've been outbid" message to `/user/queue/notifications` (see the hint in AuctionBroadcaster).
