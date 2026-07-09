/**
 * @author Jia Qianrui
 */
package com.wellnessapp.controller;

import com.wellnessapp.dto.ApiResponse;
import com.wellnessapp.dto.RecommendationDTOs.*;
import com.wellnessapp.entity.User;
import com.wellnessapp.service.RecommendationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for agentic AI recommendations.
 *
 * @author WellnessApp Team
 */
@RestController
@RequestMapping("/api")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    /**
     * Trigger the agentic AI to generate a new recommendation.
     */
    @PostMapping("/agent/recommendations")
    public ResponseEntity<ApiResponse<RecommendationResponse>> triggerRecommendation(
            @AuthenticationPrincipal User user) {
        RecommendationResponse recommendation =
                recommendationService.triggerAgent(user);
        return ResponseEntity.ok(
                ApiResponse.success("Recommendation generated successfully", recommendation));
    }

    /**
     * Get all recommendations for the authenticated user.
     */
    @GetMapping("/recommendations")
    public ResponseEntity<ApiResponse<List<RecommendationResponse>>> getRecommendations(
            @AuthenticationPrincipal User user) {
        List<RecommendationResponse> recommendations =
                recommendationService.getRecommendations(user);
        return ResponseEntity.ok(ApiResponse.success(recommendations));
    }
}
