# Slice 1 — Driver Sends GPS, Saved to Cassandra (Detailed Step-by-Step)

> **Goal:** `POST /api/drivers/location` saves a GPS ping to the existing Cassandra `vehicle_positions` table.

> **Prerequisite:** Slice 0 is done — `driver-service` builds and starts on port 8083.

---

## What You'll Learn in This Slice

| Concept | What It Means |
|---|---|
| `spring-boot-starter-data-cassandra` | Spring's Cassandra integration — auto-connects, maps Java objects to tables |
| `@Table` | Marks a Java class as a Cassandra table row |
| `@PrimaryKeyColumn` | Maps fields to Cassandra partition and clustering keys |
| `CassandraRepository` | Interface that gives you `save()`, `findAll()`, etc. for free |
| `@RestController` + `@PostMapping` | Creates a REST endpoint that accepts JSON |
| `@RequestBody` | Tells Spring to parse the HTTP body as a Java object |

---

## Step 1 — Start Cassandra

Make sure your existing Cassandra is running with the `taasim` keyspace and `vehicle_positions` table.

```bash
cd c:\Users\bouzi\Desktop\MyPersonslProjects\Taasim
docker compose up -d cassandra

# Wait ~30 seconds, then verify:
docker exec taasim-cassandra cqlsh -e "DESCRIBE KEYSPACE taasim;"
```

You should see the `vehicle_positions` table in the output. If not, run the schema init:
```bash
docker compose up cassandra-init
```

**Reminder — the existing table schema:**
```sql
CREATE TABLE taasim.vehicle_positions (
  city        TEXT,
  zone_id     INT,
  zone_name   TEXT,
  event_time  TIMESTAMP,
  taxi_id     TEXT,
  lat         DOUBLE,
  lon         DOUBLE,
  speed       DOUBLE,
  status      TEXT,       -- 'available', 'engaged', 'offline'
  PRIMARY KEY ((city, zone_id), event_time)
) WITH CLUSTERING ORDER BY (event_time DESC);
```

---

## Step 2 — Add Cassandra Dependency to `driver-service`

**File:** `backend/driver-service/pom.xml`

Add this dependency inside the `<dependencies>` block (alongside the existing `spring-boot-starter-web` and `spring-boot-starter-actuator`):

```xml
<!-- Cassandra: auto-config, CassandraRepository, @Table mapping -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-cassandra</artifactId>
</dependency>
```

Your full `<dependencies>` should now look like:
```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-cassandra</artifactId>
    </dependency>
    <dependency>
        <groupId>com.taasim</groupId>
        <artifactId>common</artifactId>
        <version>${project.version}</version>
    </dependency>
</dependencies>
```

---

## Step 3 — Configure Cassandra Connection

**File:** `backend/driver-service/src/main/resources/application.yml`

Update it to:

```yaml
server:
  port: 8083

spring:
  application:
    name: driver-service

  cassandra:
    contact-points: localhost       # Cassandra host (localhost for dev, cassandra for Docker)
    port: 9042                      # Default Cassandra CQL port
    keyspace-name: taasim           # Your existing keyspace
    local-datacenter: datacenter1   # Default for single-node Cassandra
    schema-action: none             # DON'T create/modify tables — they already exist

management:
  endpoints:
    web:
      exposure:
        include: health
```

> **`schema-action: none`** is important! Your Cassandra tables were already created by `cassandra_schema.cql`. We don't want Spring to try creating them again.

---

## Step 4 — Create the DTO (What the API Receives)

A DTO (Data Transfer Object) is the JSON shape that comes in from the HTTP request. It's NOT the database model — it's what the client sends.

**File:** `backend/driver-service/src/main/java/com/taasim/driver/dto/GpsPingRequest.java`

```java
package com.taasim.driver.dto;

/**
 * JSON body for POST /api/drivers/location
 *
 * Example:
 * {
 *   "driverId": "taxi_001",
 *   "lat": 33.5731,
 *   "lon": -7.5898,
 *   "speed": 35.0
 * }
 */
public class GpsPingRequest {

    private String driverId;
    private double lat;
    private double lon;
    private double speed;

    // Default constructor (required by Jackson JSON parser)
    public GpsPingRequest() {}

    public GpsPingRequest(String driverId, double lat, double lon, double speed) {
        this.driverId = driverId;
        this.lat = lat;
        this.lon = lon;
        this.speed = speed;
    }

    // Getters and Setters (required by Jackson)
    public String getDriverId() { return driverId; }
    public void setDriverId(String driverId) { this.driverId = driverId; }

    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }

    public double getLon() { return lon; }
    public void setLon(double lon) { this.lon = lon; }

    public double getSpeed() { return speed; }
    public void setSpeed(double speed) { this.speed = speed; }
}
```

---

## Step 5 — Create the Model (Cassandra Table Mapping)

This class maps to the EXISTING `vehicle_positions` table. The field names and types must match exactly.

**File:** `backend/driver-service/src/main/java/com/taasim/driver/model/VehiclePosition.java`

```java
package com.taasim.driver.model;

import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.Instant;

/**
 * Maps to the existing Cassandra table: taasim.vehicle_positions
 *
 * Primary Key: ((city, zone_id), event_time DESC)
 *   - Partition key: (city, zone_id) — all taxis in the same zone are co-located
 *   - Clustering key: event_time DESC — most recent position first
 */
@Table("vehicle_positions")
public class VehiclePosition {

    // ── Partition Key Part 1 ──
    @PrimaryKeyColumn(name = "city", ordinal = 0, type = PrimaryKeyType.PARTITIONED)
    private String city;

    // ── Partition Key Part 2 ──
    @PrimaryKeyColumn(name = "zone_id", ordinal = 1, type = PrimaryKeyType.PARTITIONED)
    private int zoneId;

    // ── Clustering Key ──
    @PrimaryKeyColumn(name = "event_time", ordinal = 2, type = PrimaryKeyType.CLUSTERED,
                       ordering = Ordering.DESCENDING)
    private Instant eventTime;

    @Column("zone_name")
    private String zoneName;

    @Column("taxi_id")
    private String taxiId;

    @Column("lat")
    private double lat;

    @Column("lon")
    private double lon;

    @Column("speed")
    private double speed;

    @Column("status")
    private String status;

    // Default constructor
    public VehiclePosition() {}

    // Getters and Setters
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public int getZoneId() { return zoneId; }
    public void setZoneId(int zoneId) { this.zoneId = zoneId; }

    public Instant getEventTime() { return eventTime; }
    public void setEventTime(Instant eventTime) { this.eventTime = eventTime; }

    public String getZoneName() { return zoneName; }
    public void setZoneName(String zoneName) { this.zoneName = zoneName; }

    public String getTaxiId() { return taxiId; }
    public void setTaxiId(String taxiId) { this.taxiId = taxiId; }

    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }

    public double getLon() { return lon; }
    public void setLon(double lon) { this.lon = lon; }

    public double getSpeed() { return speed; }
    public void setSpeed(double speed) { this.speed = speed; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
```

**Key points:**
- `@PrimaryKeyColumn(type = PrimaryKeyType.PARTITIONED)` → partition key
- `@PrimaryKeyColumn(type = PrimaryKeyType.CLUSTERED)` → clustering key
- The names in `@PrimaryKeyColumn(name = "...")` and `@Column("...")` must match the CQL column names exactly

---

## Step 6 — Create the Repository

Spring Data Cassandra gives you CRUD operations for free — just define an interface.

**File:** `backend/driver-service/src/main/java/com/taasim/driver/repository/VehiclePositionRepository.java`

```java
package com.taasim.driver.repository;

import com.taasim.driver.model.VehiclePosition;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;

/**
 * Spring Data Cassandra repository for vehicle_positions table.
 *
 * CassandraRepository gives us save(), findAll(), delete(), etc.
 * The generic parameters are: <EntityType, PrimaryKeyType>
 *
 * Since our primary key is composite (city + zone_id + event_time),
 * we use the entity class directly and Spring handles the rest.
 */
@Repository
public interface VehiclePositionRepository extends CassandraRepository<VehiclePosition, String> {

    // That's it! save() is inherited from CassandraRepository.
    // We'll add custom queries in later slices if needed.
}
```

> **Why `String` as the second generic?** For composite primary keys, the type here doesn't matter much since we're using `save()` which uses the entity's annotated fields. In later slices, if you need `findById()`, you'd create a separate `@PrimaryKeyClass`. For now, `save()` is all we need.

---

## Step 7 — Create the Service (Business Logic)

The service layer is where you put your business logic. For now, it's simple: take the GPS ping, figure out the zone, build a `VehiclePosition`, and save it.

**File:** `backend/driver-service/src/main/java/com/taasim/driver/service/LocationService.java`

```java
package com.taasim.driver.service;

import com.taasim.driver.dto.GpsPingRequest;
import com.taasim.driver.model.VehiclePosition;
import com.taasim.driver.repository.VehiclePositionRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Processes incoming GPS pings from drivers.
 *
 * For now (Slice 1): saves to Cassandra only.
 * Slice 2 will add: publish to Kafka raw.gps topic.
 */
@Service
public class LocationService {

    private final VehiclePositionRepository repository;

    // Constructor injection — Spring auto-wires the repository
    public LocationService(VehiclePositionRepository repository) {
        this.repository = repository;
    }

    /**
     * Process a GPS ping from a driver.
     * 
     * 1. Determine which zone the driver is in (simplified for now)
     * 2. Build a VehiclePosition entity
     * 3. Save to Cassandra vehicle_positions table
     */
    public VehiclePosition processGpsPing(GpsPingRequest request) {

        // ── Zone Lookup (simplified) ──
        // In the full system, Flink Job 1 (gps_job.py) does a proper
        // Shapely point-in-polygon lookup against the Casablanca GeoJSON.
        // For now, we use a simple grid (same formula as trip_request_producer.py).
        // This will be improved in Slice 13 (Geospatial Service with Redis).
        int zoneId = calculateZoneId(request.getLat(), request.getLon());
        String zoneName = "Zone-" + zoneId;  // Simplified — proper names come later

        // ── Build the entity ──
        VehiclePosition position = new VehiclePosition();
        position.setCity("casablanca");
        position.setZoneId(zoneId);
        position.setZoneName(zoneName);
        position.setEventTime(Instant.now());
        position.setTaxiId(request.getDriverId());
        position.setLat(request.getLat());
        position.setLon(request.getLon());
        position.setSpeed(request.getSpeed());
        position.setStatus("available");  // Default status for now

        // ── Save to Cassandra ──
        repository.save(position);

        System.out.println("📍 Saved GPS: " + request.getDriverId()
                + " → Zone " + zoneId
                + " (" + request.getLat() + ", " + request.getLon() + ")");

        return position;
    }

    /**
     * Simplified zone calculation using a 4×4 grid over Casablanca.
     * Same formula used in the existing Python simulators.
     *
     * Casablanca bounds (from OSMnx road network):
     *   LON: -7.6895 to -7.4008
     *   LAT: 33.5072 to 33.6527
     *
     * Grid cell index formula (matches gps_job.py fallback):
     *   grid_x = int(4 * (lon - LON_MIN) / (LON_MAX - LON_MIN))
     *   grid_y = int(4 * (lat - LAT_MIN) / (LAT_MAX - LAT_MIN))
     *   zone_id = (grid_y * 4) + grid_x + 1   → range 1..16
     */
    private int calculateZoneId(double lat, double lon) {
        double LON_MIN = -7.6895, LON_MAX = -7.4008;
        double LAT_MIN = 33.5072, LAT_MAX = 33.6527;

        int gridX = (int) (4 * (lon - LON_MIN) / (LON_MAX - LON_MIN));
        int gridY = (int) (4 * (lat - LAT_MIN) / (LAT_MAX - LAT_MIN));

        // Clamp to valid range
        gridX = Math.max(0, Math.min(3, gridX));
        gridY = Math.max(0, Math.min(3, gridY));

        return (gridY * 4) + gridX + 1;  // 1-indexed, range 1..16
    }
}
```

---

## Step 8 — Create the Controller (REST Endpoint)

The controller receives HTTP requests and delegates to the service.

**File:** `backend/driver-service/src/main/java/com/taasim/driver/controller/DriverController.java`

```java
package com.taasim.driver.controller;

import com.taasim.driver.dto.GpsPingRequest;
import com.taasim.driver.model.VehiclePosition;
import com.taasim.driver.service.LocationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for driver-related endpoints.
 *
 * Slice 1: POST /api/drivers/location only.
 * More endpoints will be added in later slices.
 */
@RestController
@RequestMapping("/api/drivers")
public class DriverController {

    private final LocationService locationService;

    public DriverController(LocationService locationService) {
        this.locationService = locationService;
    }

    /**
     * Receive a GPS ping from a driver.
     *
     * Request body:
     * {
     *   "driverId": "taxi_001",
     *   "lat": 33.5731,
     *   "lon": -7.5898,
     *   "speed": 35.0
     * }
     *
     * Response:
     * {
     *   "status": "ok",
     *   "driverId": "taxi_001",
     *   "zoneId": 5,
     *   "message": "Position saved to Cassandra"
     * }
     */
    @PostMapping("/location")
    public ResponseEntity<Map<String, Object>> receiveLocation(
            @RequestBody GpsPingRequest request) {

        VehiclePosition saved = locationService.processGpsPing(request);

        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "driverId", saved.getTaxiId(),
                "zoneId", saved.getZoneId(),
                "message", "Position saved to Cassandra"
        ));
    }
}
```

---

## Step 9 — Build and Test

### 9.1 Build

```bash
cd backend
mvn clean package -pl driver-service -am -DskipTests
```

`-pl driver-service` = build only driver-service
`-am` = also build its dependencies (common)

### 9.2 Start Cassandra (if not already running)

```bash
cd c:\Users\bouzi\Desktop\MyPersonslProjects\Taasim
docker compose up -d cassandra
# Wait ~30 seconds
```

### 9.3 Start the driver-service

```bash
cd backend
java -jar driver-service/target/driver-service-0.0.1-SNAPSHOT.jar
```

You should see:
```
Started DriverApplication in X.XX seconds
```

If you see a Cassandra connection error, check that:
- Cassandra is running: `docker ps | findstr cassandra`
- Port 9042 is accessible: `docker exec taasim-cassandra cqlsh -e "SELECT release_version FROM system.local;"`

### 9.4 Send a GPS Ping

```bash
curl -X POST http://localhost:8083/api/drivers/location ^
  -H "Content-Type: application/json" ^
  -d "{\"driverId\": \"taxi_001\", \"lat\": 33.5731, \"lon\": -7.5898, \"speed\": 35.0}"
```

> **Note:** On Windows CMD, use `^` for line continuation and escape quotes with `\"`. In PowerShell, use backtick `` ` `` for line continuation.

**PowerShell version:**
```powershell
Invoke-RestMethod -Uri "http://localhost:8083/api/drivers/location" `
  -Method POST `
  -ContentType "application/json" `
  -Body '{"driverId": "taxi_001", "lat": 33.5731, "lon": -7.5898, "speed": 35.0}'
```

**Expected response:**
```json
{
  "status": "ok",
  "driverId": "taxi_001",
  "zoneId": 5,
  "message": "Position saved to Cassandra"
}
```

### 9.5 Verify in Cassandra

```bash
docker exec taasim-cassandra cqlsh -e "SELECT taxi_id, zone_id, lat, lon, speed, event_time FROM taasim.vehicle_positions LIMIT 5;"
```

**Expected output:**
```
 taxi_id  | zone_id | lat     | lon     | speed | event_time
----------+---------+---------+---------+-------+---------------------------
 taxi_001 |       5 | 33.5731 | -7.5898 |  35.0 | 2026-07-26 12:00:00+0000
```

### 9.6 Send a Few More Pings (Different Locations)

```powershell
# Driver in Anfa district (zone ~2)
Invoke-RestMethod -Uri "http://localhost:8083/api/drivers/location" `
  -Method POST -ContentType "application/json" `
  -Body '{"driverId": "taxi_002", "lat": 33.5900, "lon": -7.6500, "speed": 42.0}'

# Driver in Sidi Bernoussi area (zone ~15)
Invoke-RestMethod -Uri "http://localhost:8083/api/drivers/location" `
  -Method POST -ContentType "application/json" `
  -Body '{"driverId": "taxi_003", "lat": 33.6200, "lon": -7.5100, "speed": 28.0}'
```

Verify all 3 appear in Cassandra:
```bash
docker exec taasim-cassandra cqlsh -e "SELECT taxi_id, zone_id, lat, lon FROM taasim.vehicle_positions LIMIT 10;"
```

---

## Step 10 — Understand What You Just Built

```
   Client (curl/Postman)
        │
        │  POST /api/drivers/location
        │  {"driverId":"taxi_001", "lat":33.57, "lon":-7.58, "speed":35}
        │
        ▼
   ┌──────────────────────────┐
   │    DriverController      │  ← Receives JSON, passes to service
   │    @PostMapping           │
   └────────────┬─────────────┘
                │
                ▼
   ┌──────────────────────────┐
   │    LocationService       │  ← Calculates zone, builds entity
   │    processGpsPing()      │
   └────────────┬─────────────┘
                │
                ▼
   ┌──────────────────────────┐
   │ VehiclePositionRepository│  ← .save() writes to Cassandra
   │    .save(position)       │
   └────────────┬─────────────┘
                │
                ▼
   ┌──────────────────────────┐
   │    Cassandra              │
   │    taasim.vehicle_        │
   │    positions              │
   └──────────────────────────┘
```

**What you achieved:**
- Spring Boot receives an HTTP request
- Parses JSON into a Java object automatically (Jackson)
- Business logic calculates the zone
- Spring Data Cassandra saves to your existing table
- Response returned as JSON

**What's NOT done yet (next slices):**
- ❌ No Kafka publishing (Slice 2)
- ❌ No authentication (Slice 11)
- ❌ No proper zone lookup with GeoJSON (Slice 13)
- ❌ Zone names are placeholder "Zone-X" (will use real names later)

---

## Directory Structure After Slice 1

```
backend/driver-service/
├── pom.xml                                    ← added cassandra dependency
└── src/main/
    ├── java/com/taasim/driver/
    │   ├── DriverApplication.java             ← from Slice 0
    │   ├── controller/
    │   │   └── DriverController.java          ← NEW
    │   ├── dto/
    │   │   └── GpsPingRequest.java            ← NEW
    │   ├── model/
    │   │   └── VehiclePosition.java           ← NEW
    │   ├── repository/
    │   │   └── VehiclePositionRepository.java ← NEW
    │   └── service/
    │       └── LocationService.java           ← NEW
    └── resources/
        └── application.yml                    ← updated (added cassandra config)
```

---

## Troubleshooting

### "NoHostAvailableException: All host(s) tried for query failed"
- Cassandra isn't running or isn't ready yet
- Fix: `docker compose up -d cassandra`, wait 30 seconds, try again

### "InvalidQueryException: Keyspace 'taasim' does not exist"
- Schema wasn't applied yet
- Fix: `docker compose up cassandra-init`

### "InvalidQueryException: Undefined column name zone_name"
- You might have an old schema version
- Fix: Re-run `docker exec taasim-cassandra cqlsh -e "DESCRIBE TABLE taasim.vehicle_positions;"` to check columns

### "Port 8083 already in use"
- Another instance is already running
- Fix: Kill it with `taskkill /F /IM java.exe` (careful — kills ALL Java processes) or find the specific PID

---

## ✅ Slice 1 Checklist

- [ ] `spring-boot-starter-data-cassandra` added to `pom.xml`
- [ ] `application.yml` has Cassandra connection config
- [ ] `GpsPingRequest.java` — DTO with driverId, lat, lon, speed
- [ ] `VehiclePosition.java` — @Table mapping to `vehicle_positions`
- [ ] `VehiclePositionRepository.java` — extends CassandraRepository
- [ ] `LocationService.java` — processGpsPing() calculates zone + saves
- [ ] `DriverController.java` — POST /api/drivers/location
- [ ] `mvn clean package -DskipTests` succeeds
- [ ] Service starts without errors
- [ ] POST request returns 200 with zoneId
- [ ] Row appears in Cassandra `vehicle_positions` table
- [ ] Multiple pings with different coordinates land in different zones

**When all boxes are checked → move Slice 1 to "Done", pull Slice 2 into "Doing".**
