package com.taasim.matching;

import com.taasim.matching.service.MatchingEngine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.cassandra.core.CassandraTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.testcontainers.containers.CassandraContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = {"raw.trips", "processed.matches"})
class MatchingEngineIT {

    @Container
    static CassandraContainer<?> cassandra = new CassandraContainer<>("cassandra:5.0")
            .withInitScript("schema.cql");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.cassandra.contact-points", cassandra::getHost);
        r.add("spring.cassandra.port", () -> cassandra.getMappedPort(9042));
        r.add("spring.cassandra.local-datacenter", () -> "datacenter1");
        r.add("spring.cassandra.keyspace-name", () -> "taasim");
    }

    @Autowired
    MatchingEngine matchingEngine;

    @Autowired
    CassandraTemplate cassandraTemplate;

    @Test
    void findsNearestDriver_whenPositionsExist() {
        // 1. Insert 3 driver positions into containerized Cassandra
        cassandraTemplate.getCqlOperations().execute(
                "INSERT INTO taasim.vehicle_positions (city, zone_id, taxi_id, ts, lat, lon, speed) " +
                "VALUES ('casablanca', 5, 'taxi_001', toTimestamp(now()), 33.5731, -7.5898, 30.0)"
        );
        cassandraTemplate.getCqlOperations().execute(
                "INSERT INTO taasim.vehicle_positions (city, zone_id, taxi_id, ts, lat, lon, speed) " +
                "VALUES ('casablanca', 5, 'taxi_002', toTimestamp(now()), 33.6000, -7.6000, 40.0)"
        );
        cassandraTemplate.getCqlOperations().execute(
                "INSERT INTO taasim.vehicle_positions (city, zone_id, taxi_id, ts, lat, lon, speed) " +
                "VALUES ('casablanca', 10, 'taxi_003', toTimestamp(now()), 33.5892, -7.6038, 42.5)"
        );

        // 2. Call matchingEngine.findNearestDriver(originZone = 5)
        Map<String, Object> match = matchingEngine.findNearestDriver(5);

        // 3. Assert the closest driverId is returned (taxi_001 is much closer to zone 5 center)
        assertThat(match).isNotNull();
        assertThat(match.get("driverId")).isEqualTo("taxi_001");
    }
}
