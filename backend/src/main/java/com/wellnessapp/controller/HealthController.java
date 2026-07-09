/**
 * @author Jia Qianrui
 */
package com.wellnessapp.controller;

import com.wellnessapp.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Health check controller — verifies the service is running.
 *
 * @author WellnessApp Team
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        Map<String, Object> status = Map.of(
                "status", "UP",
                "service", "wellness-backend",
                "timestamp", LocalDateTime.now().toString()
        );
        return ResponseEntity.ok(ApiResponse.success(status));
    }
}
