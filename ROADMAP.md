# Roadmap

Each phase is a self-contained learning milestone that maps to a section of the system design.

## ✅ Phase 1: Core domain + OAuth2

- [x] Keycloak realm (roles, audience mapper, demo users, self-registration). App runs as a JWT Resource Server
- [x] Just-in-time user provisioning (`users` table, `user.registered` event)
- [x] Listings
- [x] Auctions with start/end/reserve/increment, plus the SCHEDULED → ACTIVE → ENDED/CANCELLED lifecycle
- [x] Concurrency-safe bidding (pessimistic row lock), idempotency keys, anti-sniping soft close
- [x] DB-polling lifecycle scheduler (`FOR UPDATE SKIP LOCKED`, idempotent, retried)
- [x] Domain events published after commit
- [x] Unit tests + Testcontainers integration tests (including a 20-thread bid race)

## 🚧 Phase 2: Event streaming (Kafka) + real-time bids (WebSockets)

*Learn: transactional outbox, Kafka producers/consumers, partitioning, STOMP over WebSocket.*
**Walkthrough + exercises: [docs/phase-2-guide.md](docs/phase-2-guide.md)**

- [x] Kafka (KRaft) + Kafka UI in `docker-compose.yml`, `spring-kafka`, topics declared with 3 partitions
- [x] Outbox write side: events stored in `outbox_events` in the same transaction as the bid
- [x] Events keyed by `auctionId` (per-auction ordering)
- [x] STOMP endpoint `/ws`, `/topic/auctions/{id}`, JWT auth on CONNECT, destination rules
- [x] `live.html` test page; Testcontainers Kafka in the test suite
- [ ] **Exercise 1 (you):** `OutboxRelay`: outbox → Kafka with `eventId`/`eventType` headers
- [ ] **Exercise 2 (you):** `AuctionBroadcaster`: Kafka → WebSocket fan-out to every instance
- [ ] Stretch: dead-letter topic, outbox cleanup job, `outbox.pending` metric
- [ ] Idempotent consumers (dedupe by `eventId`), needed once Phase 3/4 consumers have side effects

## ⏭ Phase 3: Payments (Stripe)

*Learn: PaymentIntents, webhooks, signature verification, payment state machines, retries.*

- [ ] `payment` module: on `auction.ended` with outcome `SOLD`, create a `Payment` (PENDING) for the winner
- [ ] `POST /api/payments/initiate` creates a Stripe **PaymentIntent** (test mode) and returns its `client_secret`
- [ ] `GET /api/payments/{id}/status`
- [ ] `POST /api/webhooks/stripe`: verify the `Stripe-Signature` and handle `payment_intent.succeeded` / `payment_failed`
- [ ] Use Stripe idempotency keys, a payment deadline, retries, and publish `payment.failed` with an alert
- [ ] If the winner never pays, offer the item to the second-highest bidder (a "second chance offer")
- [ ] Test locally with the Stripe CLI (`stripe listen --forward-to localhost:8080/api/webhooks/stripe`)

## ⏭ Phase 4: Notifications, caching, protection

- [ ] `notification` module consuming Kafka: outbid, auction won, payment pending/complete
      (email via Mailpit in Docker, plus an in-app notifications table)
- [ ] Redis cache for hot auction reads (`GET /api/auctions/{id}`), invalidated by `bid.placed`
- [ ] Rate limiting on bid placement (Bucket4j + Redis) for anti-bot protection
- [ ] Admin module: list or flag suspicious bidders, view the audit trail, resolve disputes
- [ ] Optional: proxy (max) bidding, where the system bids for you up to your maximum

## ⏭ Phase 5: Observability + delivery

- [ ] Micrometer metrics (bid latency, bids/sec, scheduler lag) → Prometheus + Grafana dashboard
- [ ] Structured JSON logs with correlation ids, plus OpenTelemetry tracing
- [ ] OpenAPI docs (springdoc)
- [ ] Dockerfile / Buildpacks image, and a GitHub Actions CI that runs `mvn verify`
- [ ] Load test a "hot auction" (k6 or Gatling: 10K watchers, a burst of bids in the final seconds)
