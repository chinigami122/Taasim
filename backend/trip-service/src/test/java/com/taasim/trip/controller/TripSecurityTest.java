package com.taasim.trip.controller;

import com.taasim.trip.config.SecurityConfig;
import com.taasim.trip.model.Trip;
import com.taasim.trip.service.TripService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TripController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "jwt.secret=dev-only-secret-please-change-me-it-must-be-256-bits-long-abcd1234"
})
class TripSecurityTest {

    private static final String SECRET = "dev-only-secret-please-change-me-it-must-be-256-bits-long-abcd1234";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TripService tripService;

    private String generateToken(String userId, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + 3600000);
        var key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
                .subject(userId)
                .claim("email", userId + "@test.com")
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    @Test
    void requestTrip_noToken_returns401() throws Exception {
        mockMvc.perform(post("/api/trips/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"riderId\":\"rider_123\",\"originZone\":5,\"destinationZone\":12}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requestTrip_driverToken_returns403() throws Exception {
        String driverToken = generateToken("driver-1", "DRIVER");

        mockMvc.perform(post("/api/trips/request")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"riderId\":\"rider_123\",\"originZone\":5,\"destinationZone\":12}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void requestTrip_clientToken_returns200() throws Exception {
        String clientToken = generateToken("client-1", "CLIENT");

        Trip trip = new Trip();
        trip.setTripId("trip-abc-123");
        trip.setStatus("REQUESTED");
        trip.setOriginZone(5);
        trip.setDestZone(12);

        when(tripService.createTrip(any())).thenReturn(trip);

        mockMvc.perform(post("/api/trips/request")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"riderId\":\"rider_123\",\"originZone\":5,\"destinationZone\":12}"))
                .andExpect(status().isOk());
    }

    @Test
    void getTrip_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/trips/trip-abc-123"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getTrip_driverToken_returns200() throws Exception {
        String driverToken = generateToken("driver-1", "DRIVER");

        Trip trip = new Trip();
        trip.setTripId("trip-abc-123");
        trip.setStatus("REQUESTED");
        trip.setOriginZone(5);
        trip.setDestZone(12);

        when(tripService.getTripById("trip-abc-123")).thenReturn(trip);

        mockMvc.perform(get("/api/trips/trip-abc-123")
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isOk());
    }

    @Test
    void getTrip_clientToken_returns200() throws Exception {
        String clientToken = generateToken("client-1", "CLIENT");

        Trip trip = new Trip();
        trip.setTripId("trip-abc-123");
        trip.setStatus("REQUESTED");
        trip.setOriginZone(5);
        trip.setDestZone(12);

        when(tripService.getTripById("trip-abc-123")).thenReturn(trip);

        mockMvc.perform(get("/api/trips/trip-abc-123")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk());
    }

    @Test
    void apiDocs_noToken_bypassesSecurity() throws Exception {
        // In WebMvcTest, /v3/api-docs reaches DispatcherServlet (404) rather than being blocked with 401 Unauthorized
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isNotFound());
    }
}
