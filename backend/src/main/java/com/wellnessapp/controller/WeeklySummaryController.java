package com.wellnessapp.controller;

import com.wellnessapp.dto.ApiResponse;
import com.wellnessapp.dto.WeeklySummaryDTOs.WeeklySummaryResponse;
import com.wellnessapp.entity.User;
import com.wellnessapp.service.WeeklySummaryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for weekly health summaries.
 *
 * @author WellnessApp Team
 */
@RestController
@RequestMapping("/api/weekly-summaries")
public class WeeklySummaryController {

    private final WeeklySummaryService weeklySummaryService;

    public WeeklySummaryController(WeeklySummaryService weeklySummaryService) {
        this.weeklySummaryService = weeklySummaryService;
    }

    /**
     * Manually generate a weekly summary for the authenticated user.
     *
     * @author WellnessApp Team
     */
    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<WeeklySummaryResponse>> generate(
            @AuthenticationPrincipal User user) {
        WeeklySummaryResponse summary = weeklySummaryService.generate(user);
        return ResponseEntity.ok(ApiResponse.success("Weekly summary generated", summary));
    }

    /**
     * List historical weekly summaries for the authenticated user.
     *
     * @author WellnessApp Team
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<WeeklySummaryResponse>>> getSummaries(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(weeklySummaryService.getSummaries(user)));
    }
}
