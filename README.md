# java-spring-auction

A real-time auction platform (eBay-style), built as a **Spring Boot modular monolith**. It implements
the *"Design an Auction Platform"* system design: sellers list items, bidders compete within a timed
window, and the highest bid at closing wins.

> **Status: Phase 2 in progress.** Phase 1 (auth, listings, auctions, concurrency-safe bidding, lifecycle
> scheduler) is done. Phase 2 (transactional outbox → Kafka → WebSocket) has its plumbing in place, and two
> exercises remain: [docs/phase-2-guide.md](docs/phase-2-guide.md). See [ROADMAP.md](ROADMAP.md).

## Tech stack

| Concern | Choice |
|---|---|
| Language / framework | Java 21, Spring Boot 4.2 (Spring Framework 7, Hibernate 7) |
| Auth | OAuth2 / OpenID Connect. **Keycloak** issues JWTs and the app is a **Resource Server** |
| Database | PostgreSQL 17, schema managed by **Flyway** |
| Tests | JUnit 5, AssertJ, **Testcontainers** (real PostgreSQL), Spring Security test JWTs |
| Coming next | Kafka (event streaming), WebSockets/STOMP, Stripe, Redis |

## Architecture

The design calls for separate User, Listing, Auction, Bid, Payment, Notification and Scheduler services.
Here they are **modules inside one deployable** (one package per service). Each module exposes a public
`*Service` and keeps its repository package-private. Modules reference each other **by id only**, never
through JPA relationships. That way any module could later be split out into its own microservice.

```
com.learning.auction
├── config      Security (JWT -> roles), CORS, clock, scheduling, typed auction properties
├── common      RFC 9457 problem+json error handling, paging DTO
├── user        Just-in-time provisioning of Keycloak users into a local `users` table
├── listing     Item metadata (title, category, condition)
├── auction     Auction aggregate + lifecycle scheduler (SCHEDULED -> ACTIVE -> ENDED)
├── bid         Bid placement with row locking + idempotency keys
└── events      Domain events: user.registered, auction.started, bid.placed, auction.ended, ...
```

### How the tricky parts work

**Concurrent bids.** `POST /bids` takes a `SELECT ... FOR UPDATE` lock on the auction row, so bids on
the same auction are applied one at a time, each in a transaction that lasts a few milliseconds. Every
bid must *strictly* beat `currentPrice + minIncrement`, which rules out ties: the first bid to reach an
amount wins. `BiddingFlowIT` fires 20 identical bids at once and asserts exactly one is accepted.
*(Why not optimistic locking? On a hot auction with 1,000 simultaneous bidders, ~999 would fail and retry each round.)*

**Idempotency.** Clients send an `Idempotency-Key` header. A retried request returns the original bid
(`200`, `"replayed": true`) instead of placing a second one. The key is checked *after* the lock is
taken, so even two parallel retries can't both succeed. A unique constraint backs this up.

**Anti-sniping (soft close).** A bid in the final 2 minutes extends the end time to *bid time + 2 min*,
which gives other bidders a fair chance to respond. `originalEndTime` is kept for transparency.

**Auction timers.** `AuctionLifecycleScheduler` polls the database every second for auctions that are
due to start or end, and transitions each one in its own transaction using `FOR UPDATE SKIP LOCKED`:
- *Restart-safe:* nothing lives in memory, so after a crash or deploy the next tick catches up.
- *Multi-instance safe:* two app instances never close the same auction.
- *Idempotent:* closing twice is a no-op. Failures are logged and retried on the next tick, and a late close is logged as a warning.

**Reserve price.** This is never returned by the API. Clients only see `hasReserve` and `reserveMet`.
On close, the outcome is `SOLD` (with a `winnerId`), `RESERVE_NOT_MET` or `NO_BIDS`.

**Domain events.** These are published with `@TransactionalEventListener`, so an event only fires
*after commit*: a rolled-back bid is never announced. In Phase 1 they are only logged. Phase 2 routes
them through a transactional outbox to Kafka.

## Running locally

Prerequisites: JDK 21, Docker Desktop.

```bash
docker compose up -d          # PostgreSQL :5433, Keycloak :8180, Kafka :9092, Kafka UI :8085
./mvnw spring-boot:run        # API on :8080
```

> PostgreSQL is mapped to host port **5433** so it doesn't clash with a locally installed PostgreSQL on 5432.

### Demo users (realm `auction`)

| User | Password | Roles |
|---|---|---|
| alice | alice | user |
| bob | bob | user |
| carol | carol | user |
| admin | admin | user, admin |

The Keycloak admin console is at http://localhost:8180 (admin / admin). Self-registration is enabled
on the realm's login page, which stands in for the design's `POST /signup` and `POST /login`.

### Getting a token

The `auction-web` client is public and uses Authorization Code + PKCE, which a browser frontend should use.
For quick local testing it also allows the password grant:

```bash
curl -s -X POST http://localhost:8180/realms/auction/protocol/openid-connect/token \
  -d grant_type=password -d client_id=auction-web -d username=bob -d password=bob
```

Paste the token into [jwt.io](https://jwt.io) and look at `realm_access.roles` and `aud: auction-api`.
Those are the claims the API checks. Ready-made requests are in [`http/auction.http`](http/auction.http)
(IntelliJ HTTP client).

## API

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/users/me` | user | Current profile (creates the local user on first call) |
| POST | `/api/listings` | user | Create a listing |
| GET | `/api/listings`, `/api/listings/{id}` | public | Browse listings (`?category=GAMING`) |
| GET | `/api/users/me/listings` | user | My listings |
| POST | `/api/auctions` | user | Auction one of your listings |
| GET | `/api/auctions/{id}` | public | Auction state |
| GET | `/api/auctions/active` | public | Live auctions, ending soonest first |
| POST | `/api/auctions/{id}/cancel` | seller / admin | Seller before any bids; admin any time |
| GET | `/api/users/me/auctions` | user | Auctions I'm selling |
| POST | `/api/auctions/{id}/bids` | user | Place a bid (`Idempotency-Key` header recommended) |
| GET | `/api/auctions/{id}/bids` | public | Bid history, highest first |
| GET | `/api/users/me/bids` | user | My bids |

Errors are returned as `application/problem+json`, and rule violations include a machine-readable `code`:

```json
{ "status": 422, "title": "Business rule violated", "code": "BID_TOO_LOW",
  "detail": "Bid must be at least 105.00 USD" }
```

Codes: `BID_TOO_LOW`, `AUCTION_NOT_ACTIVE`, `AUCTION_NOT_STARTED`, `AUCTION_ENDED`, `SELLER_CANNOT_BID`,
`ALREADY_HIGHEST_BIDDER`, `INVALID_AMOUNT`, `IDEMPOTENCY_KEY_REUSED`, `AUCTION_HAS_BIDS`,
`AUCTION_NOT_CANCELLABLE`, `INVALID_DURATION`, `INVALID_RESERVE`, `START_IN_PAST`.

## Tests

```bash
./mvnw test      # fast unit tests: auction rules, role mapping (no Docker)
./mvnw verify    # + integration tests on real PostgreSQL via Testcontainers (needs Docker)
```

Integration tests use a controllable clock, so "wait an hour for the auction to end" takes no real time.
