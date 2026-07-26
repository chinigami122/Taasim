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