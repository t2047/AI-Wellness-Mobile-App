/**
 * @author Tao Yuchen
 */
package com.wellnessapp.service;

import com.wellnessapp.dto.ModelConfigDTOs.*;
import com.wellnessapp.entity.User;
import com.wellnessapp.entity.UserModelConfig;
import com.wellnessapp.repository.UserModelConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing per-user AI model configurations.
 * Users can save their own model base_url and api_key for the app to use.
 *
 * @author WellnessApp Team
 */
@Service
public class UserModelConfigService {

    private static final Logger log = LoggerFactory.getLogger(UserModelConfigService.class);

    private final UserModelConfigRepository repository;

    public UserModelConfigService(UserModelConfigRepository repository) {
        this.repository = repository;
    }

    /**
     * List all configs for the given user.
     */
    public List<ModelConfigResponse> listConfigs(User user) {
        return repository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Get the active config for a user (there should be at most one active).
     */
    public Optional<ModelConfigResponse> getActiveConfig(User user) {
        return repository.findByUserIdAndIsActiveTrue(user.getId())
                .map(this::toResponse);
    }

    /**
     * INTERNAL: Get the full active config entity (with full API key) for backend use.
     * Do NOT expose this to API responses.
     */
    public Optional<UserModelConfig> getActiveConfigEntity(User user) {
        return repository.findByUserIdAndIsActiveTrue(user.getId());
    }

    /**
     * Create or update a config for a given provider.
     * If a config with the same userId + providerName exists, update it.
     * Otherwise, create a new one. If isActive=true, deactivate all other configs.
     */
    @Transactional
    public ModelConfigResponse saveConfig(User user, ModelConfigRequest request) {
        if (request.getProviderName() == null || request.getProviderName().isBlank()) {
            throw new IllegalArgumentException("providerName is required");
        }
        if (request.getBaseUrl() == null || request.getBaseUrl().isBlank()) {
            throw new IllegalArgumentException("baseUrl is required");
        }
        if (request.getApiKey() == null || request.getApiKey().isBlank()) {
            throw new IllegalArgumentException("apiKey is required");
        }
        if (request.getModelName() == null || request.getModelName().isBlank()) {
            throw new IllegalArgumentException("modelName is required");
        }

        // If setting as active, deactivate all existing configs for this user
        boolean setActive = request.getIsActive() != null && request.getIsActive();
        if (setActive) {
            repository.findByUserIdAndIsActiveTrue(user.getId())
                    .ifPresent(existing -> {
                        existing.setIsActive(false);
                        repository.save(existing);
                    });
        }

        // Upsert: find by user + provider
        UserModelConfig config = repository
                .findByUserIdAndProviderName(user.getId(), request.getProviderName().trim())
                .orElse(UserModelConfig.builder()
                        .user(user)
                        .providerName(request.getProviderName().trim())
                        .isActive(setActive)
                        .build());

        config.setBaseUrl(request.getBaseUrl().trim());
        config.setApiKey(request.getApiKey().trim());
        config.setModelName(request.getModelName().trim());
        config.setIsActive(setActive);

        UserModelConfig saved = repository.save(config);
        log.info("User {} saved model config: provider={}, model={}, active={}",
                user.getId(), saved.getProviderName(), saved.getModelName(), saved.getIsActive());

        return toResponse(saved);
    }

    /**
     * Delete a config by ID, scoped to the owning user.
     */
    @Transactional
    public void deleteConfig(User user, Long configId) {
        UserModelConfig config = repository.findById(configId)
                .orElseThrow(() -> new IllegalArgumentException("Config not found: " + configId));
        if (!config.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Config does not belong to this user");
        }
        repository.delete(config);
        log.info("User {} deleted model config id={}", user.getId(), configId);
    }

    /**
     * Activate a specific config (deactivates all others for the same user).
     */
    @Transactional
    public ModelConfigResponse activateConfig(User user, Long configId) {
        UserModelConfig config = repository.findById(configId)
                .orElseThrow(() -> new IllegalArgumentException("Config not found: " + configId));
        if (!config.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Config does not belong to this user");
        }

        // Deactivate all
        repository.findByUserIdAndIsActiveTrue(user.getId())
                .ifPresent(existing -> {
                    existing.setIsActive(false);
                    repository.save(existing);
                });

        config.setIsActive(true);
        UserModelConfig saved = repository.save(config);
        log.info("User {} activated model config: provider={}, model={}",
                user.getId(), saved.getProviderName(), saved.getModelName());

        return toResponse(saved);
    }

    // ── Mapping ──────────────────────────────────────────────────

    private ModelConfigResponse toResponse(UserModelConfig config) {
        return ModelConfigResponse.builder()
                .id(config.getId())
                .providerName(config.getProviderName())
                .baseUrl(config.getBaseUrl())
                .apiKeyMasked(maskApiKey(config.getApiKey()))
                .modelName(config.getModelName())
                .isActive(config.getIsActive())
                .createdAt(config.getCreatedAt() != null ? config.getCreatedAt().toString() : null)
                .updatedAt(config.getUpdatedAt() != null ? config.getUpdatedAt().toString() : null)
                .build();
    }

    private String maskApiKey(String key) {
        if (key == null || key.length() <= 8) {
            return "****";
        }
        return key.substring(0, 4) + "..." + key.substring(key.length() - 4);
    }
}
