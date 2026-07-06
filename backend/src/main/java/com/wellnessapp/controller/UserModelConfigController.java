package com.wellnessapp.controller;

import com.wellnessapp.dto.ApiResponse;
import com.wellnessapp.dto.ModelConfigDTOs.*;
import com.wellnessapp.entity.User;
import com.wellnessapp.service.UserModelConfigService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for per-user AI model configuration.
 * Authenticated users can manage their own model base_url, api_key, and model name.
 *
 * @author WellnessApp Team
 */
@RestController
@RequestMapping("/api/model-config")
public class UserModelConfigController {

    private final UserModelConfigService service;

    public UserModelConfigController(UserModelConfigService service) {
        this.service = service;
    }

    /**
     * GET /api/model-config — list all configs for the authenticated user.
     */
    @GetMapping
    public ApiResponse<List<ModelConfigResponse>> listConfigs(
            @AuthenticationPrincipal User user) {
        return ApiResponse.success(service.listConfigs(user));
    }

    /**
     * GET /api/model-config/active — get the currently active config.
     */
    @GetMapping("/active")
    public ApiResponse<ModelConfigResponse> getActiveConfig(
            @AuthenticationPrincipal User user) {
        return service.getActiveConfig(user)
                .map(ApiResponse::success)
                .orElse(ApiResponse.success("No active config", null));
    }

    /**
     * POST /api/model-config — create or update a config.
     */
    @PostMapping
    public ApiResponse<ModelConfigResponse> saveConfig(
            @AuthenticationPrincipal User user,
            @RequestBody ModelConfigRequest request) {
        try {
            return ApiResponse.success(service.saveConfig(user, request));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * DELETE /api/model-config/{id} — delete a config.
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteConfig(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        try {
            service.deleteConfig(user, id);
            return ApiResponse.success("Config deleted", null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * PUT /api/model-config/{id}/activate — activate a specific config.
     */
    @PutMapping("/{id}/activate")
    public ApiResponse<ModelConfigResponse> activateConfig(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        try {
            return ApiResponse.success(service.activateConfig(user, id));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        }
    }
}
