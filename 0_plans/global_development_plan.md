# TaaSim — Vertical Slice Development Plan

## 🎯 The Method

**One slice at a time. Always working code. Learn as you go.**

```
    To Do          Doing (MAX 1)       Done
  ┌──────────┐   ┌──────────────┐   ┌──────────┐
  │ Slice 14 │   │              │   │          │
  │ Slice 13 │   │  Slice N     │   │ Slice 1  │
  │ Slice 12 │   │              │   │ Slice 2  │
  │ ...      │   │              │   │ ...      │
  └──────────┘   └──────────────┘   └──────────┘
```

**Rules:**
1. Only **ONE slice** in "Doing" at any time
2. Each slice goes **all the way down**: API → Service → Kafka/DB → Test
3. Don't move to the next slice until current one **works**
4. Learn only what the current slice needs — nothing more

---

## 🧱 Slice 0 — Project Skeleton

> **"I can run `mvn clean package` and all services start."**

**What you build:**
- Maven parent POM with modules: `common`, `driver-service`, `trip-service`, `matching-service`, `auth-service`, `billing-service`, `geospatial-service`, `gateway-service`
- Each module has just: `pom.xml` + one `@SpringBootApplication` class + `application.yml` with its port
- `common` module has one empty class (placeholder)

**What you learn:**
- Maven multi-module setup
- `spring-boot-starter-web` dependency
- `application.yml` basics (`server.port`)

**How to test:**
```bash
cd backend
mvn clean package -DskipTests
java -jar driver-service/target/driver-service-0.0.1.jar
# → Visit http://localhost:8083/actuator/health → {"status":"UP"}
```

**Done when:** All 8 JARs build, each starts on its own port, `/actuator/health` returns UP.

---

## 🧱 Slice 1 — Driver Sends GPS, Saved to Cassandra

> **"As a driver, I send my GPS location, and Spring Boot saves it to Cassandra."**

**What you build:**

```
driver-service/
└── src/main/java/com/taasim/driver/
    ├── DriverApplication.java          (already exists from Slice 0)
    ├── controller/
    │   └── DriverController.java       ← POST /api/drivers/location
    ├── dto/
    │   └── GpsPingRequest.java         ← { lat, lon, speed }
    ├── model/
    │   └── VehiclePosition.java        ← @Table("vehicle_positions")
    ├── repository/
    │   └── VehiclePositionRepository.java  ← extends CassandraRepository
    └── service/
        └── LocationService.java        ← saves to Cassandra
```

**What you learn:**
- `spring-boot-starter-data-cassandra`
- `@Table`, `@PrimaryKey`, `@Column` annotations
- `CassandraRepository` interface
- `@RestController`, `@PostMapping`
- Connecting to your existing Cassandra container

**application.yml:**
```yaml
spring:
  cassandra:
    contact-points: localhost
    port: 9042
    keyspace-name: taasim
    local-datacenter: datacenter1
```

**How to test:**
```bash
# Make sure Cassandra is running (docker compose up cassandra)
# Start driver-service
# Then:
curl -X POST http://localhost:8083/api/drivers/location \
  -H "Content-Type: application/json" \
  -d '{"driverId": "taxi_001", "lat": 33.5731, "lon": -7.5898, "speed": 35.0}'

# Check Cassandra:
docker exec taasim-cassandra cqlsh -e "SELECT * FROM taasim.vehicle_positions LIMIT 5;"
```

**Done when:** POST returns 200, row appears in Cassandra `vehicle_positions` table.

---

## 🧱 Slice 2 — Driver GPS Published to Kafka

> **"When a driver sends GPS, it also gets published to Kafka `raw.gps` so Flink can process it."**

**What you build:**

```
driver-service/
└── src/main/java/com/taasim/driver/
    └── kafka/
        └── GpsEventProducer.java       ← KafkaTemplate.send("raw.gps", event)
```

**What you change:**
- `LocationService.java` — after saving to Cassandra, also call `GpsEventProducer.send()`

**What you learn:**
- `spring-kafka` dependency
- `KafkaTemplate` — how to produce a message
- JSON serialization with `JsonSerializer`

**How to test:**
```bash
# Send GPS ping via API
curl -X POST http://localhost:8083/api/drivers/location \
  -H "Content-Type: application/json" \
  -d '{"driverId": "taxi_001", "lat": 33.5731, "lon": -7.5898, "speed": 35.0}'

# Read from Kafka to verify:
docker exec taasim-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic raw.gps --from-beginning --max-messages 1
```

**Done when:** GPS ping appears in both Cassandra AND Kafka `raw.gps` topic. Flink Job 1 picks it up (check Flink dashboard at localhost:8081).

---

## 🧱 Slice 3 — Client Requests a Trip

> **"As a client, I request a ride via POST, it's saved and published to Kafka."**

**What you build:**

```
trip-service/
└── src/main/java/com/taasim/trip/
    ├── TripApplication.java            (already exists)
    ├── controller/
    │   └── TripController.java         ← POST /api/trips/request
    ├── dto/
    │   └── TripRequest.java            ← { originZone, destinationZone }
    ├── model/
    │   └── Trip.java                   ← @Table("trips")
    ├── repository/
    │   └── TripRepository.java
    ├── service/
    │   └── TripService.java            ← saves to Cassandra + publishes to Kafka
    └── kafka/
        └── TripEventProducer.java      ← KafkaTemplate.send("raw.trips", event)
```

**What you learn:**
- Second service with same patterns (Cassandra + Kafka)
- UUID generation for `trip_id`
- Mapping to existing Cassandra `trips` table

**How to test:**
```bash
curl -X POST http://localhost:8082/api/trips/request \
  -H "Content-Type: application/json" \
  -d '{"riderId": "rider_1234", "originZone": 5, "destinationZone": 12}'

# Check response: {"tripId": "uuid...", "status": "REQUESTED"}

# Verify in Cassandra:
docker exec taasim-cassandra cqlsh -e "SELECT * FROM taasim.trips LIMIT 5;"

# Verify in Kafka:
docker exec taasim-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic raw.trips --from-beginning --max-messages 1
```

**Done when:** POST returns tripId, trip appears in Cassandra with status `REQUESTED`, event appears in Kafka `raw.trips`.

---

## 🧱 Slice 4 — Matching Service Hears Trips

> **"The matching service listens to Kafka and prints 'Trip received!' when a new trip request arrives."**

**What you build:**

```
matching-service/
└── src/main/java/com/taasim/matching/
    ├── MatchingApplication.java        (already exists)
    └── kafka/
        └── TripRequestConsumer.java    ← @KafkaListener(topics = "raw.trips")
```

**What you learn:**
- `@KafkaListener` annotation
- `ConsumerConfig` and `JsonDeserializer`
- How Kafka consumer groups work

**The code is literally this simple:**
```java
@KafkaListener(topics = "raw.trips", groupId = "matching-service")
public void onTripRequest(String message) {
    System.out.println("🚕 Trip received: " + message);
}
```

**How to test:**
```bash
# Start matching-service
# Request a trip from Slice 3:
curl -X POST http://localhost:8082/api/trips/request \
  -H "Content-Type: application/json" \
  -d '{"riderId": "rider_1234", "originZone": 5, "destinationZone": 12}'

# Check matching-service console logs → should see "🚕 Trip received: {...}"
```

**Done when:** Matching service logs the trip request JSON when a trip is created.

---

## 🧱 Slice 5 — Matching Service Finds Nearest Driver

> **"When a trip comes in, the matching service queries Cassandra for nearby drivers and picks the closest one."**

**What you build:**

```
matching-service/
└── src/main/java/com/taasim/matching/
    ├── service/
    │   └── MatchingEngine.java         ← findNearestDriver() logic
    ├── util/
    │   └── GeoUtils.java              ← haversine() + computeEta()
    └── kafka/
        ├── TripRequestConsumer.java    ← (update: now calls MatchingEngine)
        └── MatchEventProducer.java     ← publishes to processed.matches
```

**What you learn:**
- Querying Cassandra from a non-repository context (or via CassandraTemplate)
- Porting Python Haversine to Java (reference `trip_matcher_job.py` lines 44–51)
- Producing a match event back to Kafka

**Key logic (ported from your existing Python):**
```java
public static double haversine(double lat1, double lon1, double lat2, double lon2) {
    double R = 6_371_000;
    double dLat = Math.toRadians(lat2 - lat1);
    double dLon = Math.toRadians(lon2 - lon1);
    double a = Math.sin(dLat/2) * Math.sin(dLat/2)
             + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
             * Math.sin(dLon/2) * Math.sin(dLon/2);
    return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}
```

**How to test:**
```bash
# 1. Send some driver GPS pings (Slice 1) to populate vehicle_positions
# 2. Request a trip (Slice 3)
# 3. Check matching-service logs → should print the matched driver + ETA
# 4. Check Kafka processed.matches topic:
docker exec taasim-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic processed.matches --from-beginning --max-messages 1
```

**Done when:** Trip request → matching service finds closest driver → publishes match event to `processed.matches` with driverId + ETA.

---

## 🧱 Slice 6 — Trip Service Listens for Matches

> **"When a match happens, the trip service updates the trip status from REQUESTED to MATCHED."**

**What you build:**

```
trip-service/
└── src/main/java/com/taasim/trip/
    └── kafka/
        └── MatchEventConsumer.java     ← @KafkaListener(topics = "processed.matches")
```

**What you change:**
- `TripService.java` — add `updateTripStatus(tripId, "MATCHED", driverId, eta)`

**What you learn:**
- Cassandra UPDATE queries
- Consuming events to trigger state changes

**How to test:**
```bash
# Request a trip → matching happens → trip automatically updates
curl http://localhost:8082/api/trips/{tripId}
# → {"tripId": "...", "status": "MATCHED", "driverId": "taxi_001", "etaSeconds": 120}
```

**Done when:** `GET /api/trips/{id}` returns status `MATCHED` with assigned driverId after matching.

---

## 🧱 Slice 7 — Driver Accepts a Trip

> **"As a driver, I accept a matched trip, and the trip status becomes ACCEPTED."**

**What you build:**

```
driver-service/
└── src/main/java/com/taasim/driver/
    ├── controller/
    │   └── DriverController.java       ← add PUT /api/drivers/trips/{id}/accept
    ├── kafka/
    │   ├── MatchEventConsumer.java      ← listens to processed.matches (assigns trip to driver)
    │   └── TripStatusProducer.java      ← publishes to trip.status topic
    └── service/
        └── DriverService.java          ← acceptTrip() logic
```

**New Kafka topic used:** `trip.status`

**What you learn:**
- Event choreography: one event triggers another
- Driver state management (AVAILABLE → BUSY)

**How to test:**
```bash
# After matching, driver accepts:
curl -X PUT http://localhost:8083/api/drivers/trips/{tripId}/accept

# Check trip status:
curl http://localhost:8082/api/trips/{tripId}
# → {"status": "ACCEPTED"}
```

**Done when:** Driver accepts → trip.status event published → Trip Service updates trip to `ACCEPTED`.

---

## 🧱 Slice 8 — Driver Completes the Ride

> **"As a driver, I mark a ride as complete, and a `trip.completed` event fires."**

**What you build:**

```
driver-service/
└── controller/DriverController.java
    ← add PUT /api/drivers/trips/{id}/start
    ← add PUT /api/drivers/trips/{id}/complete
```

**What you change:**
- `DriverService` — `startRide()` sets trip to IN_PROGRESS, `completeRide()` publishes to `trip.completed`
- `TripStatusProducer` — produces events for START and COMPLETE

**Flow:**
```
Driver accepts → ACCEPTED
Driver picks up passenger → PUT /start → IN_PROGRESS
Driver arrives at destination → PUT /complete → COMPLETED + event to trip.completed
```

**How to test:**
```bash
curl -X PUT http://localhost:8083/api/drivers/trips/{tripId}/start
curl http://localhost:8082/api/trips/{tripId}   # → IN_PROGRESS

curl -X PUT http://localhost:8083/api/drivers/trips/{tripId}/complete
curl http://localhost:8082/api/trips/{tripId}   # → COMPLETED

# Verify trip.completed event in Kafka
```

**Done when:** Full lifecycle works: REQUESTED → MATCHED → ACCEPTED → IN_PROGRESS → COMPLETED. All through Kafka events.

---

## 🧱 Slice 9 — Simple User Registration

> **"As a user, I register with my email, password, and role (CLIENT/DRIVER/ADMIN)."**

**What you build:**

```
auth-service/
└── src/main/java/com/taasim/auth/
    ├── AuthApplication.java            (already exists)
    ├── controller/
    │   └── AuthController.java         ← POST /api/auth/register
    ├── dto/
    │   └── RegisterRequest.java        ← { email, password, fullName, role }
    ├── model/
    │   └── User.java                   ← @Entity (JPA → PostgreSQL)
    ├── repository/
    │   └── UserRepository.java         ← extends JpaRepository
    └── service/
        └── AuthService.java            ← hash password, save user
```

**What you learn:**
- `spring-boot-starter-data-jpa` + PostgreSQL
- `@Entity`, `@Id`, `@GeneratedValue`
- `BCryptPasswordEncoder` for password hashing
- Your first non-Cassandra database in this project

**How to test:**
```bash
curl -X POST http://localhost:8081/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email": "soufiane@test.com", "password": "123456", "fullName": "Soufiane", "role": "DRIVER"}'

# → {"id": "uuid...", "email": "soufiane@test.com", "role": "DRIVER"}

# Check PostgreSQL:
docker exec taasim-postgres psql -U taasim -c "SELECT * FROM users;"
```

**Done when:** User saved to PostgreSQL with hashed password.

---

## 🧱 Slice 10 — Login + JWT Token

> **"As a registered user, I login and receive a JWT token."**

**What you build:**

```
auth-service/
└── src/main/java/com/taasim/auth/
    ├── controller/
    │   └── AuthController.java         ← add POST /api/auth/login
    ├── security/
    │   ├── JwtTokenProvider.java       ← generate + validate JWT
    │   └── SecurityConfig.java         ← permit /register, /login
    └── dto/
        ├── LoginRequest.java           ← { email, password }
        └── AuthResponse.java           ← { accessToken, role }
```

**What you learn:**
- Spring Security basics (`SecurityFilterChain`)
- JWT generation with `jjwt` library
- Permitting public endpoints, securing others

**How to test:**
```bash
curl -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "soufiane@test.com", "password": "123456"}'

# → {"accessToken": "eyJhbGci...", "role": "DRIVER"}

# Decode JWT at jwt.io → see sub, role, exp claims
```

**Done when:** Login returns valid JWT. Invalid password returns 401.

---

## 🧱 Slice 11 — Protect Endpoints with JWT

> **"Only authenticated users can send GPS or request trips. Invalid tokens get 401."**

**What you build:**

```
common/
└── src/main/java/com/taasim/common/
    └── security/
        ├── JwtUtils.java              ← shared JWT parse/validate
        └── JwtAuthFilter.java         ← OncePerRequestFilter
```

**What you change:**
- `driver-service` — add `JwtAuthFilter` to SecurityConfig
- `trip-service` — add `JwtAuthFilter` to SecurityConfig
- Both services now require `Authorization: Bearer <token>` header

**What you learn:**
- Sharing code via Maven module (`common`)
- `OncePerRequestFilter` — extract and validate JWT on every request
- `@PreAuthorize("hasRole('DRIVER')")` — role-based access

**How to test:**
```bash
# Without token → 401
curl -X POST http://localhost:8083/api/drivers/location \
  -d '{"lat": 33.57, "lon": -7.58, "speed": 30}'
# → 401 Unauthorized

# With token → 200
TOKEN=$(curl -s -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "soufiane@test.com", "password": "123456"}' | jq -r .accessToken)

curl -X POST http://localhost:8083/api/drivers/location \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"lat": 33.57, "lon": -7.58, "speed": 30}'
# → 200 OK
```

**Done when:** All endpoints require JWT. Wrong role gets 403. No token gets 401.

---

## 🧱 Slice 12 — API Gateway Routes Everything

> **"All requests go through one gateway at port 8080, which routes to the right service."**

**What you build:**

```
gateway-service/
└── src/main/java/com/taasim/gateway/
    ├── GatewayApplication.java         (already exists)
    └── config/
        └── RouteConfig.java            ← route definitions
```

**application.yml:**
```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: auth-service
          uri: http://localhost:8081
          predicates:
            - Path=/api/auth/**
        - id: trip-service
          uri: http://localhost:8082
          predicates:
            - Path=/api/trips/**
        - id: driver-service
          uri: http://localhost:8083
          predicates:
            - Path=/api/drivers/**
```

**What you learn:**
- Spring Cloud Gateway
- Route predicates and filters
- Why a gateway exists (single entry point)

**How to test:**
```bash
# Everything through port 8080 now:
curl http://localhost:8080/api/auth/login ...     # → routed to 8081
curl http://localhost:8080/api/trips/request ...   # → routed to 8082
curl http://localhost:8080/api/drivers/location ...# → routed to 8083
```

**Done when:** All API calls work through `localhost:8080`. Direct service ports still work too.

---

## 🧱 Slice 13 — Redis for Real-Time Driver Positions

> **"Driver positions are cached in Redis for fast proximity queries."**

**What you build:**

```
geospatial-service/
└── src/main/java/com/taasim/geospatial/
    ├── GeospatialApplication.java      (already exists)
    ├── controller/
    │   └── GeospatialController.java   ← GET /internal/drivers/nearby
    ├── service/
    │   └── ProximityService.java       ← Redis GEOADD, GEORADIUS
    └── config/
        └── RedisConfig.java
```

**What you change:**
- `driver-service/LocationService.java` — after saving GPS, also call geospatial-service to update Redis

**What you learn:**
- `spring-boot-starter-data-redis`
- Redis GEO commands: `GEOADD`, `GEORADIUS`
- Inter-service REST calls with `RestTemplate` or `WebClient`

**How to test:**
```bash
# Driver sends GPS → position stored in Redis
# Query nearby:
curl "http://localhost:8084/internal/drivers/nearby?lat=33.57&lon=-7.58&radius=2000"
# → [{"driverId": "taxi_001", "distanceMeters": 450.2}]
```

**Done when:** Nearby query returns drivers that sent GPS within the radius.

---

## 🧱 Slice 14 — Matching Uses Geospatial Instead of Cassandra

> **"The matching service now queries the fast Redis geospatial index instead of scanning Cassandra."**

**What you change:**
- `matching-service/MatchingEngine.java` — replace Cassandra query with HTTP call to geospatial-service `/internal/drivers/nearby`

**What you learn:**
- Service-to-service communication
- Why a geospatial index is faster than scanning database rows

**Done when:** Matching still works, but now uses Redis proximity → much faster.

---

## 🧱 Slice 15 — Fare Calculation on Trip Complete

> **"When a trip completes, the billing service calculates the fare."**

**What you build:**

```
billing-service/
└── src/main/java/com/taasim/billing/
    ├── BillingApplication.java         (already exists)
    ├── model/
    │   └── BillingRecord.java          ← @Entity (JPA → PostgreSQL)
    ├── repository/
    │   └── BillingRecordRepository.java
    ├── service/
    │   ├── FareCalculator.java         ← base + distance + time × surge
    │   └── BillingService.java
    └── kafka/
        └── TripCompletedConsumer.java  ← @KafkaListener(topics = "trip.completed")
```

**What you learn:**
- Another JPA/PostgreSQL service (second after auth)
- Business logic: fare = baseFare + (distanceKm × 3.50) + (durationMin × 0.50) × surge
- Event-driven: billing is triggered by an event, not an API call

**How to test:**
```bash
# Complete a trip (Slice 8) → billing service auto-calculates fare
# Check PostgreSQL:
docker exec taasim-postgres psql -U taasim -c "SELECT * FROM billing_records;"
# → trip_id, total_fare=45.50, commission=6.83, driver_payout=38.67
```

**Done when:** Trip completion → billing record automatically created in PostgreSQL with fare breakdown.

---

## 🧱 Slice 16 — Stripe Payment Integration

> **"The billing service charges the client's card via Stripe."**

**What you build:**

```
billing-service/
└── service/
    └── StripeService.java              ← create customer, charge, refund
```

**What you change:**
- `BillingService.java` — after calculating fare, call `StripeService.charge()`
- `auth-service` — create Stripe customer on CLIENT registration

**What you learn:**
- Stripe Java SDK (`stripe-java`)
- PaymentIntent API (test mode)
- Storing `stripeCustomerId` on the user

**How to test:**
- Complete a trip → check Stripe Dashboard (test mode) → see the charge
- Use Stripe test card: `4242 4242 4242 4242`

**Done when:** Trip completion → Stripe test charge appears in dashboard → billing record has `stripePaymentId`.

---

## 🧱 Slice 17 — Client Trip History + Billing

> **"As a client, I see my past trips and what I was charged."**

**What you build:**
- `trip-service` — `GET /api/trips/history` (filter by riderId from JWT)
- `billing-service` — `GET /api/billing/history` (filter by clientId from JWT)
- `billing-service` — `GET /api/billing/trips/{id}` (fare breakdown)

**Done when:** Client can see trip history + fare details via API.

---

## 🧱 Slice 18 — Driver Earnings

> **"As a driver, I see my earnings per trip and total."**

**What you build:**
- `billing-service` — `GET /api/drivers/earnings` (filter by driverId)
- `driver-service` — `GET /api/drivers/trips/history`

**Done when:** Driver sees payout per trip + total earnings via API.

---

## 🧱 Slice 19 — WebSocket: Live Driver Tracking

> **"As a client, after matching, I see the driver's location updating on a map in real-time."**

**What you build:**
- `driver-service` — WebSocket endpoint using STOMP + SockJS
- Client subscribes to `/topic/trips/{tripId}/location`
- Driver GPS pings get broadcast to subscribers

**What you learn:**
- `spring-boot-starter-websocket`
- STOMP protocol, `SimpMessagingTemplate`
- Real-time push instead of polling

**Done when:** Open WebSocket connection → driver sends GPS → client receives location updates instantly.

---

## 🧱 Slice 20 — Admin: View All Drivers and Trips

> **"As an admin, I see all active drivers and all trips."**

**What you build:**
- `driver-service` — `GET /api/admin/drivers` (requires ADMIN role)
- `trip-service` — `GET /api/admin/trips` (requires ADMIN role)
- `billing-service` — `GET /api/admin/billing/summary`

**Done when:** Admin JWT can access these endpoints, CLIENT/DRIVER tokens get 403.

---

## 🧱 Slice 21 — React Client App (Basic)

> **"A client can login, request a ride, and see the trip status in a browser."**

**What you build:**
- `frontend/client-app/` — React + Vite
- Pages: Login → Request Ride → Trip Status

**What you learn:**
- React basics (if new), Axios for API calls
- JWT storage in localStorage
- Simple form → POST → display response

**Done when:** Login in browser → request trip → see "MATCHED" status update.

---

## 🧱 Slice 22 — React Client: Map + Live Tracking

> **"The client sees Casablanca on a map and watches the driver move after matching."**

**What you build:**
- Leaflet map component with Casablanca zones (from existing GeoJSON)
- WebSocket connection to see driver moving
- Pick origin/destination by clicking on map

**Done when:** Client clicks map to request ride → sees driver icon moving toward them.

---

## 🧱 Slice 23 — React Driver App

> **"A driver can go online, see incoming trip requests, and accept them."**

**What you build:**
- `frontend/driver-app/` — React + Vite
- Pages: Login → Go Online (sends GPS) → Incoming Trip popup → Accept → Complete

**Done when:** Driver goes online → receives trip notification → accepts → completes.

---

## 🧱 Slice 24 — React Admin Dashboard

> **"An admin sees a fleet map, trip list, and billing overview."**

**What you build:**
- `frontend/admin-app/` — React + Vite
- Pages: Login → Fleet Map (all drivers) → Trip Table → Billing Summary

**Done when:** Admin sees live driver positions on map + trip list + revenue numbers.

---

## 🧱 Slice 25 — Full Demo Flow

> **"Open 3 browser tabs. Admin watches, client requests, driver accepts. Everything works."**

**What you do:**
1. Admin tab: sees fleet dashboard
2. Client tab: requests a ride
3. Driver tab: receives notification, accepts
4. Client tab: watches driver moving on map
5. Driver tab: completes ride
6. Client tab: sees fare, confirms payment
7. Admin tab: sees new trip in list, billing updated

**Done when:** Full flow runs without errors. You can demo it to anyone.

---

## 📊 Summary

| Slice | Feature | Service(s) | You Learn |
|---|---|---|---|
| 0 | Project skeleton | All | Maven multi-module |
| 1 | GPS → Cassandra | driver | Spring Data Cassandra |
| 2 | GPS → Kafka | driver | Spring Kafka Producer |
| 3 | Trip request | trip | REST + Cassandra + Kafka |
| 4 | Matching listens | matching | @KafkaListener |
| 5 | Find nearest driver | matching | Haversine, Cassandra queries |
| 6 | Trip status update | trip | Kafka consumer, state machine |
| 7 | Driver accepts | driver | Event choreography |
| 8 | Ride lifecycle | driver + trip | Full event flow |
| 9 | User registration | auth | JPA + PostgreSQL |
| 10 | Login + JWT | auth | Spring Security, JWT |
| 11 | Protect endpoints | common + all | JWT filter, @PreAuthorize |
| 12 | API Gateway | gateway | Spring Cloud Gateway |
| 13 | Redis proximity | geospatial | Redis GEO commands |
| 14 | Match via Redis | matching | Service-to-service calls |
| 15 | Fare calculation | billing | Business logic, Kafka consumer |
| 16 | Stripe payment | billing + auth | Stripe SDK |
| 17 | Client history | trip + billing | Paginated queries |
| 18 | Driver earnings | billing + driver | Aggregation queries |
| 19 | WebSocket tracking | driver | STOMP, SimpMessagingTemplate |
| 20 | Admin endpoints | all | Role-based access |
| 21 | React client (basic) | frontend | React + Axios + JWT |
| 22 | Client map + tracking | frontend | Leaflet + WebSocket |
| 23 | React driver app | frontend | Geolocation API |
| 24 | React admin dashboard | frontend | Charts + data tables |
| 25 | Full demo | all | Integration testing |

---

## 🚦 Start Here

Pull **Slice 0** into "Doing". Create the Maven skeleton. Make all services start. That's your first win.

```
To Do              Doing               Done
┌──────────────┐  ┌──────────────┐   ┌──────────────┐
│ Slice 1      │  │              │   │              │
│ Slice 2      │  │  Slice 0     │   │              │
│ Slice 3      │  │              │   │              │
│ ...          │  │              │   │              │
│ Slice 25     │  │              │   │              │
└──────────────┘  └──────────────┘   └──────────────┘
```
