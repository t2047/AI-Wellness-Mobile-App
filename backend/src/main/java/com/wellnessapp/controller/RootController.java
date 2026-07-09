/**
 * @author Jia Qianrui
 */
package com.wellnessapp.controller;

import com.wellnessapp.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Public root endpoint for quick browser-based backend verification.
 *
 * @author WellnessApp Team
 */
@RestController
public class RootController {

    /**
     * Show a simple backend status response at http://localhost:8080/.
     */
    @GetMapping({"/", "/api"})
    public ResponseEntity<ApiResponse<Map<String, Object>>> root() {
        Map<String, Object> status = Map.of(
                "status", "UP",
                "service", "wellness-backend",
                "healthEndpoint", "/api/health",
                "timestamp", LocalDateTime.now().toString()
        );
        return ResponseEntity.ok(ApiResponse.success(status));
    }
}
