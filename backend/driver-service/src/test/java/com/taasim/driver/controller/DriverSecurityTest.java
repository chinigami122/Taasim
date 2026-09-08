package com.taasim.driver.controller;

import com.taasim.driver.config.SecurityConfig;
import com.taasim.driver.model.VehiclePosition;
import com.taasim.driver.service.DriverService;
import com.taasim.driver.service.LocationService;
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
import java.time.Instant;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DriverController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "jwt.secret=dev-only-secret-please-change-me-it-must-be-256-bits-long-abcd1234"
})
class DriverSecurityTest {

    private static final String SECRET = "dev-only-secret-please-change-me-it-must-be-256-bits-long-abcd1234";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LocationService locationService;

    @MockitoBean
    private DriverService driverService;

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
    void location_noToken_returns401() throws Exception {
        mockMvc.perform(post("/api/drivers/location")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"driverId\":\"taxi_001\",\"lat\":33.57,\"lon\":-7.58,\"speed\":30}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void location_clientToken_returns403() throws Exception {
        String clientToken = generateToken("client-1", "CLIENT");

        mockMvc.perform(post("/api/drivers/location")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"driverId\":\"taxi_001\",\"lat\":33.57,\"lon\":-7.58,\"speed\":30}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void location_driverToken_returns200() throws Exception {
        String driverToken = generateToken("driver-1", "DRIVER");

        VehiclePosition vp = new VehiclePosition();
        vp.setTaxiId("taxi_001");
        vp.setZoneId(5);
        when(locationService.processGpsPing(any())).thenReturn(vp);

        mockMvc.perform(post("/api/drivers/location")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"driverId\":\"taxi_001\",\"lat\":33.57,\"lon\":-7.58,\"speed\":30}"))
                .andExpect(status().isOk());
    }

    @Test
    void acceptTrip_noToken_returns401() throws Exception {
        mockMvc.perform(put("/api/drivers/trips/taxi_001/accept"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptTrip_clientToken_returns403() throws Exception {
        String clientToken = generateToken("client-1", "CLIENT");

        mockMvc.perform(put("/api/drivers/trips/taxi_001/accept")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void acceptTrip_driverToken_returns200() throws Exception {
        String driverToken = generateToken("driver-1", "DRIVER");

        when(driverService.acceptTrip("taxi_001")).thenReturn(true);
        when(driverService.getActiveTrip("taxi_001")).thenReturn("trip-123");

        mockMvc.perform(put("/api/drivers/trips/taxi_001/accept")
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isOk());
    }

    @Test
    void apiDocs_noToken_bypassesSecurity() throws Exception {
        // In WebMvcTest, /v3/api-docs reaches DispatcherServlet (404) rather than being blocked with 401 Unauthorized
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isNotFound());
    }
}
