# Slice 0 — Project Skeleton (Detailed Step-by-Step)

> **Goal:** Run `mvn clean package` and all 8 services build successfully. Each one starts on its own port and responds to `/actuator/health`.

---

## Prerequisites

Before starting, make sure you have installed:
- **Java 21** — [Download Temurin JDK 21](https://adoptium.net/)
- **Maven 3.9+** — [Download Maven](https://maven.apache.org/download.cgi)
- **Your IDE** — IntelliJ IDEA (recommended) or VS Code with Java Extension Pack

Verify:
```bash
java -version     # → openjdk 21.x.x
mvn -version      # → Apache Maven 3.9.x
```

---

## Step 1 — Create the `backend/` Directory

Inside your existing Taasim project, create the backend folder:

```
Taasim/
├── src/                    ← existing (Flink, simulators, etc.)
├── data/                   ← existing
├── docker-compose.yml      ← existing
└── backend/                ← NEW — you create this now
```

```bash
cd c:\Users\bouzi\Desktop\MyPersonslProjects\Taasim
mkdir backend
cd backend
```

---

## Step 2 — Create the Parent POM

Create `backend/pom.xml`. This is the **parent POM** that holds all 8 modules together.

**File:** `backend/pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <!-- Spring Boot Parent — gives us dependency management + plugin defaults -->
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.4.1</version>
        <relativePath/>
    </parent>

    <groupId>com.taasim</groupId>
    <artifactId>taasim-backend</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <packaging>pom</packaging>
    <name>TaaSim Backend</name>
    <description>Fleet Telematics and Ride-Matching Microservices</description>

    <properties>
        <java.version>21</java.version>
    </properties>

    <!-- All child modules -->
    <modules>
        <module>common</module>
        <module>auth-service</module>
        <module>gateway-service</module>
        <module>driver-service</module>
        <module>trip-service</module>
        <module>matching-service</module>
        <module>geospatial-service</module>
        <module>billing-service</module>
    </modules>
</project>
```

**What this does:**
- Declares Spring Boot 3.4.1 as the parent (manages all dependency versions for you)
- Sets Java 21
- Lists all 8 modules — Maven will build them in order

---

## Step 3 — Create the `common` Module

This is a shared library (no Spring Boot app — just a plain JAR).

### 3.1 Create the directory structure

```bash
mkdir -p common/src/main/java/com/taasim/common
```

### 3.2 Create `common/pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.taasim</groupId>
        <artifactId>taasim-backend</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>

    <artifactId>common</artifactId>
    <name>TaaSim Common</name>
    <description>Shared DTOs, utilities, and security classes</description>

    <!-- NOT a Spring Boot app — just a library JAR -->
    <!-- No spring-boot-maven-plugin here -->

    <dependencies>
        <!-- We'll add dependencies as we need them in later slices -->
    </dependencies>
</project>
```

### 3.3 Create a placeholder class

**File:** `common/src/main/java/com/taasim/common/CommonConstants.java`

```java
package com.taasim.common;

/**
 * Shared constants used across all TaaSim microservices.
 * This class will grow as we add shared DTOs and utilities.
 */
public final class CommonConstants {
    
    public static final String KAFKA_TOPIC_RAW_GPS = "raw.gps";
    public static final String KAFKA_TOPIC_RAW_TRIPS = "raw.trips";
    public static final String KAFKA_TOPIC_PROCESSED_GPS = "processed.gps";
    public static final String KAFKA_TOPIC_PROCESSED_MATCHES = "processed.matches";
    public static final String KAFKA_TOPIC_PROCESSED_DEMAND = "processed.demand";
    public static final String KAFKA_TOPIC_TRIP_STATUS = "trip.status";
    public static final String KAFKA_TOPIC_TRIP_COMPLETED = "trip.completed";
    public static final String KAFKA_TOPIC_BILLING_COMPLETED = "billing.completed";

    private CommonConstants() {} // prevent instantiation
}
```

---

## Step 4 — Create the `driver-service` Module

This is the first real Spring Boot microservice. We'll use it as the **template** for all others.

### 4.1 Create the directory structure

```bash
mkdir -p driver-service/src/main/java/com/taasim/driver
mkdir -p driver-service/src/main/resources
```

### 4.2 Create `driver-service/pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.taasim</groupId>
        <artifactId>taasim-backend</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>

    <artifactId>driver-service</artifactId>
    <name>TaaSim Driver Service</name>
    <description>Driver telemetry, GPS ingestion, trip acceptance</description>

    <dependencies>
        <!-- Web: gives us @RestController, embedded Tomcat -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Actuator: gives us /actuator/health endpoint -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

        <!-- Our shared common module -->
        <dependency>
            <groupId>com.taasim</groupId>
            <artifactId>common</artifactId>
            <version>${project.version}</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

### 4.3 Create the Application class

**File:** `driver-service/src/main/java/com/taasim/driver/DriverApplication.java`

```java
package com.taasim.driver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DriverApplication {

    public static void main(String[] args) {
        SpringApplication.run(DriverApplication.class, args);
    }
}
```

### 4.4 Create `application.yml`

**File:** `driver-service/src/main/resources/application.yml`

```yaml
server:
  port: 8083

spring:
  application:
    name: driver-service

management:
  endpoints:
    web:
      exposure:
        include: health
```

### 4.5 Test it builds and runs

```bash
cd backend
mvn clean package -pl driver-service -am -DskipTests
java -jar driver-service/target/driver-service-0.0.1-SNAPSHOT.jar
```

Visit `http://localhost:8083/actuator/health` → should return `{"status":"UP"}`

Press `Ctrl+C` to stop.

---

## Step 5 — Create the Remaining 6 Services

Repeat the **exact same pattern** from Step 4 for each service. The only things that change are the **module name**, **package name**, **port**, and **class name**.

### Service Table — Copy-Paste Reference

| Module | Package | Port | Application Class | Description |
|---|---|---|---|---|
| `trip-service` | `com.taasim.trip` | 8082 | `TripApplication.java` | Trip lifecycle management |
| `matching-service` | `com.taasim.matching` | 8085 | `MatchingApplication.java` | Ride matching engine |
| `auth-service` | `com.taasim.auth` | 8081 | `AuthApplication.java` | Authentication + JWT |
| `billing-service` | `com.taasim.billing` | 8086 | `BillingApplication.java` | Payments + billing |
| `geospatial-service` | `com.taasim.geospatial` | 8084 | `GeospatialApplication.java` | Proximity + zones |
| `gateway-service` | `com.taasim.gateway` | 8080 | `GatewayApplication.java` | API Gateway |

### For each service, create these files:

**1. Directory structure:**
```bash
mkdir -p {service-name}/src/main/java/com/taasim/{package}
mkdir -p {service-name}/src/main/resources
```

**2. `{service-name}/pom.xml`** — copy from driver-service, change:
- `<artifactId>` → service name
- `<name>` → service display name
- `<description>` → service description

**3. `{Service}Application.java`** — copy from DriverApplication, change:
- Package declaration
- Class name

**4. `application.yml`** — copy from driver-service, change:
- `server.port` → correct port
- `spring.application.name` → service name

### ⚠️ Special Case: `gateway-service`

The gateway uses a **different Spring Boot starter** — it uses `spring-cloud-starter-gateway` instead of `spring-boot-starter-web`.

**`gateway-service/pom.xml`** needs these changes:

```xml
<dependencies>
    <!-- Gateway: reactive routing (replaces spring-boot-starter-web) -->
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-gateway</artifactId>
    </dependency>

    <!-- Actuator -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>

    <!-- Common -->
    <dependency>
        <groupId>com.taasim</groupId>
        <artifactId>common</artifactId>
        <version>${project.version}</version>
    </dependency>
</dependencies>

<!-- Spring Cloud BOM for version management -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>2024.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

> **Why different?** Spring Cloud Gateway is a reactive (non-blocking) application. It uses Netty instead of Tomcat. That's why you can't mix `spring-boot-starter-web` and `spring-cloud-starter-gateway` in the same module.

---

## Step 6 — Build Everything Together

From the `backend/` root:

```bash
cd backend
mvn clean package -DskipTests
```

**Expected output:**
```
[INFO] Reactor Summary:
[INFO]
[INFO] TaaSim Backend ................................ SUCCESS
[INFO] TaaSim Common ................................. SUCCESS
[INFO] TaaSim Auth Service ........................... SUCCESS
[INFO] TaaSim Gateway Service ........................ SUCCESS
[INFO] TaaSim Driver Service ......................... SUCCESS
[INFO] TaaSim Trip Service ........................... SUCCESS
[INFO] TaaSim Matching Service ....................... SUCCESS
[INFO] TaaSim Geospatial Service ..................... SUCCESS
[INFO] TaaSim Billing Service ........................ SUCCESS
[INFO] BUILD SUCCESS
```

If any module fails, fix it before moving on. Common errors:
- Wrong parent reference → check `<parent>` block in child POMs
- Missing package → make sure `com.taasim.{x}` folder exists with the Application class
- Gateway conflict → make sure gateway has NO `spring-boot-starter-web` dependency

---

## Step 7 — Test Each Service Starts

Open **7 separate terminals** (or use one terminal and background each):

```bash
# Terminal 1
java -jar auth-service/target/auth-service-0.0.1-SNAPSHOT.jar

# Terminal 2
java -jar trip-service/target/trip-service-0.0.1-SNAPSHOT.jar

# Terminal 3
java -jar driver-service/target/driver-service-0.0.1-SNAPSHOT.jar

# Terminal 4
java -jar geospatial-service/target/geospatial-service-0.0.1-SNAPSHOT.jar

# Terminal 5
java -jar matching-service/target/matching-service-0.0.1-SNAPSHOT.jar

# Terminal 6
java -jar billing-service/target/billing-service-0.0.1-SNAPSHOT.jar

# Terminal 7
java -jar gateway-service/target/gateway-service-0.0.1-SNAPSHOT.jar
```

### Verify each one:

```bash
curl http://localhost:8080/actuator/health    # Gateway
curl http://localhost:8081/actuator/health    # Auth
curl http://localhost:8082/actuator/health    # Trip
curl http://localhost:8083/actuator/health    # Driver
curl http://localhost:8084/actuator/health    # Geospatial
curl http://localhost:8085/actuator/health    # Matching
curl http://localhost:8086/actuator/health    # Billing
```

**Each should return:**
```json
{"status":"UP"}
```

---

## Step 8 — Verify Existing Pipeline Still Works

Make sure you didn't break anything:

```bash
# From the project root (not backend/)
docker compose up -d
# Wait ~40 seconds for containers to initialize

# Check all existing containers are running:
docker ps --format "table {{.Names}}\t{{.Status}}"

# Start simulators (if you want to verify full pipeline)
python src/simulators/vehicle_gps_producer.py --broker localhost:9092 --speed 10
```

**Done when:** Existing Flink pipeline works AND all 7 Spring Boot services start independently.

---

## Final Directory Structure After Slice 0

```
Taasim/
├── src/                              ← UNCHANGED
├── data/                             ← UNCHANGED
├── docker-compose.yml                ← UNCHANGED
├── global_development_plan.md
├── fleet_telematics_migration_plan.md
│
└── backend/                          ← NEW
    ├── pom.xml                       ← Parent POM
    │
    ├── common/
    │   ├── pom.xml
    │   └── src/main/java/com/taasim/common/
    │       └── CommonConstants.java
    │
    ├── auth-service/
    │   ├── pom.xml
    │   └── src/main/
    │       ├── java/com/taasim/auth/
    │       │   └── AuthApplication.java
    │       └── resources/
    │           └── application.yml        (port: 8081)
    │
    ├── gateway-service/
    │   ├── pom.xml
    │   └── src/main/
    │       ├── java/com/taasim/gateway/
    │       │   └── GatewayApplication.java
    │       └── resources/
    │           └── application.yml        (port: 8080)
    │
    ├── driver-service/
    │   ├── pom.xml
    │   └── src/main/
    │       ├── java/com/taasim/driver/
    │       │   └── DriverApplication.java
    │       └── resources/
    │           └── application.yml        (port: 8083)
    │
    ├── trip-service/
    │   ├── pom.xml
    │   └── src/main/
    │       ├── java/com/taasim/trip/
    │       │   └── TripApplication.java
    │       └── resources/
    │           └── application.yml        (port: 8082)
    │
    ├── matching-service/
    │   ├── pom.xml
    │   └── src/main/
    │       ├── java/com/taasim/matching/
    │       │   └── MatchingApplication.java
    │       └── resources/
    │           └── application.yml        (port: 8085)
    │
    ├── geospatial-service/
    │   ├── pom.xml
    │   └── src/main/
    │       ├── java/com/taasim/geospatial/
    │       │   └── GeospatialApplication.java
    │       └── resources/
    │           └── application.yml        (port: 8084)
    │
    └── billing-service/
        ├── pom.xml
        └── src/main/
            ├── java/com/taasim/billing/
            │   └── BillingApplication.java
            └── resources/
                └── application.yml        (port: 8086)
```

---

## ✅ Slice 0 Checklist

- [ ] `backend/pom.xml` — parent POM with 8 modules
- [ ] `common/` — builds as plain JAR with `CommonConstants.java`
- [ ] `auth-service/` — starts on port 8081, health returns UP
- [ ] `gateway-service/` — starts on port 8080, health returns UP (uses Spring Cloud Gateway)
- [ ] `driver-service/` — starts on port 8083, health returns UP
- [ ] `trip-service/` — starts on port 8082, health returns UP
- [ ] `matching-service/` — starts on port 8085, health returns UP
- [ ] `geospatial-service/` — starts on port 8084, health returns UP
- [ ] `billing-service/` — starts on port 8086, health returns UP
- [ ] `mvn clean package -DskipTests` — BUILD SUCCESS for all modules
- [ ] Existing pipeline (docker compose + Flink + simulators) still works

**When all boxes are checked → move Slice 0 to "Done", pull Slice 1 into "Doing".**
