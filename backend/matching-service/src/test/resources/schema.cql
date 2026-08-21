CREATE KEYSPACE IF NOT EXISTS taasim WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};
USE taasim;

CREATE TABLE IF NOT EXISTS vehicle_positions (
    city text,
    zone_id int,
    taxi_id text,
    ts timestamp,
    lat double,
    lon double,
    speed double,
    PRIMARY KEY ((city, zone_id), ts, taxi_id)
) WITH CLUSTERING ORDER BY (ts DESC);

CREATE TABLE IF NOT EXISTS trips (
    city text, 
    date_bucket text, 
    created_at timestamp, 
    trip_id text,
    status text, 
    taxi_id text, 
    eta_seconds int,
    origin_zone int, 
    dest_zone int,
    PRIMARY KEY ((city, date_bucket), created_at)
) WITH CLUSTERING ORDER BY (created_at DESC);
