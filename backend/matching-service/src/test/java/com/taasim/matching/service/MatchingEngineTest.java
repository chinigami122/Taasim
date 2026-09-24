package com.taasim.matching.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.startsWith;

class MatchingEngineTest {

    private MatchingEngine matchingEngine;
    private MockRestServiceServer server;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8084");
        server = MockRestServiceServer.bindTo(builder).build();
        matchingEngine = new MatchingEngine(builder.build(), objectMapper, "http://localhost:8084", 5000);
    }

    @Test
    void findNearestDriver_returnsClosestDriver() {
        String mockResponse = """
            [
                {"driverId":"taxi_001","distanceMeters":320.5,"lat":33.5731,"lon":-7.5898},
                {"driverId":"taxi_002","distanceMeters":890.0,"lat":33.5800,"lon":-7.5900}
            ]
            """;

        server.expect(requestTo(startsWith("http://localhost:8084/internal/drivers/nearby")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON));

        Map<String, Object> result = matchingEngine.findNearestDriver(5);

        assertThat(result).isNotNull();
        assertThat(result.get("driverId")).isEqualTo("taxi_001");
        assertThat(result.get("distanceMeters")).isEqualTo(320.5);
        assertThat(result.get("etaSeconds")).isNotNull();
        server.verify();
    }

    @Test
    void findNearestDriver_noDrivers_returnsNull() {
        server.expect(requestTo(startsWith("http://localhost:8084/internal/drivers/nearby")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        Map<String, Object> result = matchingEngine.findNearestDriver(5);

        assertThat(result).isNull();
        server.verify();
    }

    @Test
    void findNearestDriver_serverError_returnsNullGracefully() {
        server.expect(requestTo(startsWith("http://localhost:8084/internal/drivers/nearby")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        Map<String, Object> result = matchingEngine.findNearestDriver(5);

        assertThat(result).isNull();
        server.verify();
    }
}
