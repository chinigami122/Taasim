package com.taasim.trip.repository;

import com.taasim.trip.model.Trip;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TripRepository extends CassandraRepository<Trip, String> {
    // save() is inherited — that's all we need for Slice 3
}