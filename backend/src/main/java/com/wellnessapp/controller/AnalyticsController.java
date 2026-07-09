/**
 * @author Zhang Xuhan
 */
package com.wellnessapp.controller;

import com.wellnessapp.dto.AnalyticsDTOs.DashboardResponse;
import com.wellnessapp.dto.ApiResponse;
import com.wellnessapp.entity.User;
import com.wellnessapp.service.AnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for analytics dashboard data.
 *
 * @author Xuhan Zhang
 */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /**
     * Get dashboard metrics for the authenticated user.
     */
    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) Integer days) {
        DashboardResponse dashboard = analyticsService.getDashboard(user, days);
        return ResponseEntity.ok(ApiResponse.success(dashboard));
    }
}
