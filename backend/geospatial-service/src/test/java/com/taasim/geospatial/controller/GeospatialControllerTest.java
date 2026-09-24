package com.taasim.geospatial.controller;

import com.taasim.geospatial.config.SecurityConfig;
import com.taasim.geospatial.service.ProximityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GeospatialController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class GeospatialControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProximityService proximityService;

    @Test
    void updatePosition_returns200() throws Exception {
        mockMvc.perform(put("/internal/drivers/taxi_001/position")
                        .param("lat", "33.5731")
                        .param("lon", "-7.5898"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.driverId").value("taxi_001"));

        verify(proximityService).updatePosition("taxi_001", 33.5731, -7.5898);
    }

    @Test
    void findNearby_returnsDriverList() throws Exception {
        when(proximityService.findNearby(33.5731, -7.5898, 2000.0))
                .thenReturn(List.of(Map.of("driverId", "taxi_001", "distanceMeters", 350.0, "lat", 33.5731, "lon", -7.5898)));

        mockMvc.perform(get("/internal/drivers/nearby")
                        .param("lat", "33.5731")
                        .param("lon", "-7.5898")
                        .param("radius", "2000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].driverId").value("taxi_001"))
                .andExpect(jsonPath("$[0].distanceMeters").value(350.0));
    }

    @Test
    void removeDriver_returns200() throws Exception {
        mockMvc.perform(delete("/internal/drivers/taxi_001/position"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("removed"));

        verify(proximityService).removeDriver("taxi_001");
    }
}
