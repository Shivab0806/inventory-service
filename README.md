# Inventory Service

A production-grade **Inventory** microservice built with **Spring Boot 3.3 / Java 17**, backed by **PostgreSQL** through JPA/Hibernate. It follows the exact same layered architecture, conventions, and tooling as **Product Service**, so the two are easy to read, run, and extend side by side.

## How this aligns with Product Service

| Aspect | Product Service | Inventory Service |
|---|---|---|
| Layering | Controller → Service (iface+impl) → Repository → Entity | Same |
| DTO boundary | Request/Response DTOs, explicit Mapper | Same, plus operation-specific DTOs (see below) |
| Error contract | `ErrorResponse` via `GlobalExceptionHandler` | Identical shape and handler structure |
| Migrations | Liquibase, `db/changelog/db.changelog-master.xml` | Same convention |
| Optimistic locking | `@Version` on entity | Same, **plus** pessimistic row locking for stock mutations (see below) |
| Config | `application.yml`, env-var overridable, `dev`/`test` profiles | Same, port `8081` instead of `8080` |
| Docker | Multi-stage `Dockerfile` + `docker-compose.yml` | Same, on a shared `services-net` external network |
| Testing | JUnit 5, Mockito, MockMvc, H2 `test` profile | Same |

**Deliberately not shared:** databases. Each service owns its own PostgreSQL instance/schema. Inventory never joins across to Product's tables.

### Relationship to Product Service (loosely coupled, by design)

Inventory stores only `productId` (Product's primary key) and a denormalized `sku` — no foreign key, no JPA relationship, no synchronous HTTP call at request time. This means:

- Each service can be deployed, scaled, and evolve its schema independently.
- Inventory has no hard runtime dependency on Product Service being reachable.
- The trade-off: Inventory doesn't validate that a `productId` you send actually exists in Product Service. That's an intentional simplification for now.

**Where a future integration point goes:** if/when you want Inventory to verify a `productId` against Product Service, or Product Service to show stock alongside product details, the natural options are:
1. A synchronous check via `WebClient`/`RestClient` calling `GET /api/v1/products/{id}` at creation time (simplest, adds a runtime dependency).
2. An async event (e.g. `ProductCreated`/`ProductDeleted` on a message broker) that Inventory consumes to reconcile records — keeps services decoupled at runtime.
3. An API Gateway / BFF layer that composes both services' responses for a client, so neither service calls the other directly.

Both compose files already join a shared `services-net` Docker network in anticipation of option 1, without requiring it today.

## Domain model

Unlike Product's plain CRUD, stock levels are a **shared, contended resource** — many callers may adjust the same inventory record concurrently (an order reserving stock, a warehouse restocking, another order cancelling). So beyond CRUD, Inventory exposes explicit stock-movement operations instead of allowing stock fields to be edited via a blind `PUT`:

| Concept | Field | Meaning |
|---|---|---|
| On-hand | `quantityOnHand` | Physical units in the warehouse |
| Reserved | `quantityReserved` | Units allocated to open orders, not yet shipped |
| Available | `quantityAvailable` (computed) | `onHand - reserved`; what can still be sold |
| Low stock | `lowStock` (computed) | `available <= reorderThreshold` |

Invariant enforced at both the DB (`CHECK` constraint) and service layer: `quantityReserved <= quantityOnHand`.

### Concurrency strategy

Stock mutation endpoints (`adjust`, `reserve`, `release`, `fulfill`) use `SELECT ... FOR UPDATE` (`@Lock(PESSIMISTIC_WRITE)`) in addition to the `@Version` optimistic lock already present on the entity. Reservations are exactly the kind of operation where two concurrent requests racing to reserve "the last unit" must not both succeed — a pessimistic lock on that row makes the check-then-write atomic, while `@Version` still protects the plain CRUD paths (`update`/metadata edits) from lost updates without paying for a lock there.

## Tech Stack

Same as Product Service: Java 17, Spring Boot 3.3.2, Spring Web, Spring Data JPA, Bean Validation, PostgreSQL, Liquibase, Lombok, springdoc-openapi, Actuator, JUnit 5 / Mockito / AssertJ / MockMvc / H2.

## Getting Started

### Option A — Docker Compose (fastest)

```bash
docker network create services-net   # one-time, shared with product-service
docker compose up --build
```

API available at `http://localhost:8081`.

### Option B — Run locally against your own Postgres

1. Create a database and user:
   ```sql
   CREATE DATABASE inventory_service_db;
   CREATE USER inventory_service WITH PASSWORD 'inventory_service';
   GRANT ALL PRIVILEGES ON DATABASE inventory_service_db TO inventory_service;
   ```
2. Export config (or rely on the defaults, which match the SQL above):
   ```bash
   export DB_URL=jdbc:postgresql://localhost:5432/inventory_service_db
   export DB_USERNAME=inventory_service
   export DB_PASSWORD=inventory_service
   export SERVER_PORT=8081
   ```
3. Run:
   ```bash
   mvn spring-boot:run
   ```

### Running both services together

```bash
docker network create services-net
(cd product-service && docker compose up --build -d)
(cd inventory-service && docker compose up --build -d)
```

Product Service → `http://localhost:8080`, Inventory Service → `http://localhost:8081`. Each container can reach the other by its Docker container name (`product-service`, `inventory-service`) on `services-net`.

### Running tests

```bash
mvn test
```

Tests run against an in-memory H2 database (`test` profile) — no external DB needed.

### API Docs

Once running: `http://localhost:8081/swagger-ui.html`

## API Reference

Base path: `/api/v1/inventory`

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/inventory` | Open a new inventory record for a product |
| GET | `/api/v1/inventory/{id}` | Get by inventory id |
| GET | `/api/v1/inventory/product/{productId}` | Get by productId |
| GET | `/api/v1/inventory?sku=&page=&size=&sortBy=&direction=` | Paginated search/list |
| PUT | `/api/v1/inventory/{id}` | Update metadata (SKU, reorder threshold) |
| DELETE | `/api/v1/inventory/{id}` | Delete |
| POST | `/api/v1/inventory/{id}/adjust` | Adjust on-hand by a signed delta |
| POST | `/api/v1/inventory/{id}/reserve` | Reserve stock for an order |
| POST | `/api/v1/inventory/{id}/release` | Release a reservation back to available |
| POST | `/api/v1/inventory/{id}/fulfill` | Fulfill a reservation (ships the order) |

### Create an inventory record

```bash
curl -X POST http://localhost:8081/api/v1/inventory \
  -H "Content-Type: application/json" \
  -d '{
        "productId": 1,
        "sku": "SKU-1001",
        "initialQuantity": 250,
        "reorderThreshold": 20
      }'
```

Response `201 Created`:

```json
{
  "id": 1,
  "productId": 1,
  "sku": "SKU-1001",
  "quantityOnHand": 250,
  "quantityReserved": 0,
  "quantityAvailable": 250,
  "reorderThreshold": 20,
  "lowStock": false,
  "version": 0,
  "createdAt": "2026-07-17T09:00:00Z",
  "updatedAt": "2026-07-17T09:00:00Z"
}
```

### Reserve stock for an order

```bash
curl -X POST http://localhost:8081/api/v1/inventory/1/reserve \
  -H "Content-Type: application/json" \
  -d '{ "quantity": 3, "reference": "ORDER-98421" }'
```

### Insufficient stock example

`409 Conflict`:

```json
{
  "status": 409,
  "error": "Conflict",
  "message": "Cannot reserve 500 units for inventory id 1: only 247 available",
  "path": "/api/v1/inventory/1/reserve"
}
```

### Restock (positive adjustment)

```bash
curl -X POST http://localhost:8081/api/v1/inventory/1/adjust \
  -H "Content-Type: application/json" \
  -d '{ "quantity": 100, "reason": "Supplier delivery PO-4471" }'
```

### Fulfill a reservation (order shipped)

```bash
curl -X POST http://localhost:8081/api/v1/inventory/1/fulfill \
  -H "Content-Type: application/json" \
  -d '{ "quantity": 3, "reference": "ORDER-98421" }'
```

## Project layout

```
inventory-service/
├── pom.xml
├── Dockerfile
├── docker-compose.yml
└── src
    ├── main
    │   ├── java/com/example/inventoryservice
    │   │   ├── InventoryServiceApplication.java
    │   │   ├── controller/InventoryController.java
    │   │   ├── service/InventoryService.java
    │   │   ├── service/impl/InventoryServiceImpl.java
    │   │   ├── repository/InventoryRepository.java
    │   │   ├── entity/Inventory.java
    │   │   ├── dto/
    │   │   │   ├── InventoryCreateRequestDTO.java
    │   │   │   ├── InventoryUpdateRequestDTO.java
    │   │   │   ├── StockAdjustmentRequestDTO.java
    │   │   │   ├── StockReservationRequestDTO.java
    │   │   │   ├── InventoryResponseDTO.java
    │   │   │   └── PagedResponse.java
    │   │   ├── mapper/InventoryMapper.java
    │   │   └── exception/
    │   │       ├── ResourceNotFoundException.java
    │   │       ├── DuplicateResourceException.java
    │   │       ├── InsufficientStockException.java
    │   │       ├── ErrorResponse.java
    │   │       └── GlobalExceptionHandler.java
    │   └── resources
    │       ├── application.yml
    │       └── db/changelog
    │           ├── db.changelog-master.xml
    │           └── changes/001-create-inventory-table.xml
    └── test/java/com/example/inventoryservice
        ├── InventoryServiceApplicationTests.java
        ├── service/InventoryServiceImplTest.java
        └── controller/InventoryControllerTest.java
```

## Next steps you may want to add

- AuthN/AuthZ (Spring Security + OAuth2/JWT), consistent with whatever's chosen for Product Service.
- One of the Product↔Inventory integration options described above.
- Distributed tracing (Micrometer Tracing + OTel) — becomes valuable once requests span both services.
- CI pipeline (build, test, image scan, push) — same GitHub Actions pattern suggested for Product Service.
