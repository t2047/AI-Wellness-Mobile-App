/**
 * @author Tao Yuchen
 */
package com.wellnessapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * RAG (Retrieval-Augmented Generation) DTOs for MSD drug knowledge queries.
 *
 * @author WellnessApp Team
 */
public class RagDTOs {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RagAskRequest {
        private String question;
        private Integer topK;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RagAskResponse {
        private boolean success;
        private String answer;
        private List<RagSource> sources;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RagSource {
        private int rank;
        private double score;
        private String title;
        private String sectionTitle;
        private int chunkId;
        private String sourceUrl;
        private String snippet;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RagStatusResponse {
        private String indexPath;
        private String builtAt;
        private int documentCount;
        private int sourceCount;
        private String corpusGlob;
        private String deepseekModel;
        private String doubaoModel;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RagReindexRequest {
        private boolean force;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RagReindexResponse {
        private String taskId;
        private String status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RagReindexStatusResponse {
        private String taskId;
        private String status;
        private int progress;
        private String phase;
        private String message;
        private String error;
        private Map<String, Object> result;
        private String startedAt;
        private String completedAt;
    }
}
