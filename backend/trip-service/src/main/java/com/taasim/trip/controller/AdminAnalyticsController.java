package com.taasim.trip.controller;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.dao.DataAccessException;
import org.springframework.data.cassandra.core.CassandraTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@PreAuthorize("hasRole('ADMIN')")
public class AdminAnalyticsController {
    private final CassandraTemplate cassandra;

    public AdminAnalyticsController(CassandraTemplate cassandra) {
        this.cassandra = cassandra;
    }

    @GetMapping("/api/admin/analytics/ride-requests")
    public HourlyReport rideRequests(@RequestParam Instant from, @RequestParam Instant to) {
        if (!from.equals(from.truncatedTo(ChronoUnit.HOURS))
                || !to.equals(to.truncatedTo(ChronoUnit.HOURS))
                || !from.isBefore(to) || Duration.between(from, to).compareTo(Duration.ofDays(31)) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use whole UTC hours and a range of up to 31 days");
        }

        List<HourlyCount> counts = new ArrayList<>();
        Instant processedAt = null;
        try {
            for (var day = from.atOffset(ZoneOffset.UTC).toLocalDate();
                    day.compareTo(to.minusSeconds(1).atOffset(ZoneOffset.UTC).toLocalDate()) <= 0;
                    day = day.plusDays(1)) {
                var rows = cassandra.getCqlOperations().queryForList("""
                        SELECT hour, zone, count, processed_at FROM app_ride_requests_hourly
                        WHERE city = ? AND day = ? AND hour >= ? AND hour < ?
                        """, "casablanca", day, from, to);
                for (var row : rows) {
                    counts.add(new HourlyCount((Instant) row.get("hour"), (Integer) row.get("zone"),
                            (Long) row.get("count")));
                    var timestamp = (Instant) row.get("processed_at");
                    if (processedAt == null || timestamp.isAfter(processedAt)) {
                        processedAt = timestamp;
                    }
                }
            }
        } catch (DataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Ride request analytics unavailable", ex);
        }
        return new HourlyReport(processedAt, counts);
    }

    public record HourlyCount(Instant hour, int zone, long count) {}

    public record HourlyReport(Instant processedAt, List<HourlyCount> counts) {}
}
