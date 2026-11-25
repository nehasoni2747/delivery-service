# Delivery Order and Pricing APIs

Two independently deployable Spring Boot services for evaluating and creating delivery orders. The Delivery Order API owns the order database; the Pricing and Eligibility API owns pricing rules and is stateless. The order service never reads or writes pricing data.

## Requirements and run

- Java 21 and Maven 3.9+
- Docker with Compose (for the PostgreSQL-backed local setup)

From the repository root, start the services and PostgreSQL:

```sh
docker compose up --build
```

The order API listens on `http://localhost:8080`; pricing listens on `http://localhost:8081`. Health checks are available at `/actuator/health`, API docs at `/swagger-ui.html`, and the OpenAPI contracts are in `docs/openapi/`. PostgreSQL is exposed on port `5432` for local development. Set `POSTGRES_PASSWORD` to override the development-only Compose default before deployment.

Build and run each service independently:

```sh
mvn -f pricing-eligibility-api/pom.xml test
mvn -f delivery-order-api/pom.xml test
```

The order API expects PostgreSQL in its default runtime configuration. For a local database, start only `postgres` with Compose, or override `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD`. Flyway owns schema creation. The pricing API has no database.

Example request (synthetic data only):

```sh
curl -i http://localhost:8080/api/v1/delivery-orders \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 8fc11c85-630d-4044-a620-cc862b8e19d5' \
  -H 'X-Correlation-Id: demo-001' \
  -d '{
    "customerReference":"DEMO-ORDER-001",
    "sender":{"name":"Sender Example","postalCode":"1012 AB","countryCode":"NL"},
    "recipient":{"name":"Recipient Example","postalCode":"5611EM","countryCode":"NL"},
    "parcel":{"weightInGrams":2750},
    "deliveryType":"NEXT_DAY"
  }'

curl http://localhost:8080/api/v1/delivery-orders/{orderId}
```

The pricing API can be evaluated directly with `POST /api/v1/pricing/evaluations`; its request and response models are described by the OpenAPI contract. Business rejections return HTTP 200 with `eligible:false` and a stable rejection code. The order API stores the rejected evaluation as `REJECTED` and returns the resulting order resource. A repeated idempotency key with identical normalized content returns the existing resource; a changed payload returns RFC 9457 Problem Details with HTTP 409.

## Design and operational assumptions

- Idempotency keys are unique in PostgreSQL. A `PRICING_PENDING` order is committed before the upstream call; this makes concurrent requests converge on one row and avoids keeping a database transaction open over network I/O. Replays can observe `PRICING_PENDING` while the first call is running. A process crash in this brief window leaves a visible pending order; a production deployment should add a monitored recovery worker.
- Pricing calls have bounded connect/response timeouts and no automatic retries. A timeout, server error, or malformed response is persisted as `PRICING_UNAVAILABLE`, never as a false confirmation. Clients can safely replay with the same key; this starter does not re-evaluate a persisted unavailable order.
- Prices use `BigDecimal` and are stored with scale 2. Pricing uses the published delivery-type base price, one weight surcharge, and one EUR 1.00 urban surcharge when either endpoint is within 1000AA–1099ZZ. The assessment's illustrative component list conflicts with its base-price table; this implementation follows the table and exposes the actual component arithmetic.
- Orders store UTC instants; API timestamps are rendered with the `Europe/Amsterdam` offset. Pricing rule changes do not alter stored order prices.
- The Compose configuration is for local development, not production secret management. Production should use managed PostgreSQL, TLS, a secret manager, restricted network policies, and OAuth2 client credentials or mTLS between services, with role-based authorization for customer-facing access.

## Privacy and GDPR

Collect only the sender/recipient names, postal codes, and country needed to arrange delivery, and use customer references only for order association. Use the data solely to fulfill and support delivery. Define a documented retention schedule with legal counsel and delete or anonymize personal fields when the delivery and statutory retention purposes expire; preserve only records that must be retained for tax, accounting, or legal claims, with restricted access. Support data-subject access and erasure requests subject to those lawful retention duties. Keep audit events focused on order IDs, event type, and timestamps rather than copies of personal data. Structured logs contain correlation and event identifiers, not request bodies, names, postal addresses, or sensitive headers.

## Performance and incomplete production work

The synchronous design is sized as a starter for the stated 25 normal / 150 peak requests per second: use bounded HTTP timeouts, a small Hikari connection pool, database uniqueness rather than in-memory coordination, and horizontal stateless API instances. PostgreSQL pool size and servlet thread limits should be load-tested against the target deployment; production should add rate limiting/back-pressure, dashboards and alerts, authentication/authorization, secret injection, and a pending-order recovery mechanism. No distributed tracing exporter, authentication provider, or load-test profile is included.

See [architecture](docs/architecture.md) and [decision records](docs/decisions/) for boundaries and trade-offs.
