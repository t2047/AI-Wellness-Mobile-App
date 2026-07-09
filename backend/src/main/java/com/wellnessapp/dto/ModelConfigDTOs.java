/**
 * @author Tao Yuchen
 */
package com.wellnessapp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTOs for user model configuration endpoints.
 *
 * @author WellnessApp Team
 */
public class ModelConfigDTOs {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ModelConfigRequest {
        private String providerName;
        private String baseUrl;
        private String apiKey;
        private String modelName;
        private Boolean isActive;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ModelConfigResponse {
        private Long id;
        private String providerName;
        private String baseUrl;
        private String apiKeyMasked;      // "sk-...xxxx" — never return the full key
        private String modelName;
        private Boolean isActive;
        private String createdAt;
        private String updatedAt;
    }
}
