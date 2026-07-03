package com.wellnessapp.controller;

import com.wellnessapp.dto.ApiResponse;
import com.wellnessapp.dto.RagDTOs.*;
import com.wellnessapp.entity.User;
import com.wellnessapp.service.RagService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for RAG (drug knowledge) queries.
 * Proxies requests to the Python agent RAG service.
 *
 * @author WellnessApp Team
 */
@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    /**
     * Get the current RAG index status.
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<RagStatusResponse>> getStatus(
            @AuthenticationPrincipal User user) {
        RagStatusResponse status = ragService.getStatus();
        return ResponseEntity.ok(ApiResponse.success(status));
    }

    /**
     * Start an asynchronous reindex of the RAG corpus.
     */
    @PostMapping("/reindex")
    public ResponseEntity<ApiResponse<RagReindexResponse>> startReindex(
            @AuthenticationPrincipal User user,
            @RequestBody(required = false) RagReindexRequest request) {
        boolean force = request != null && request.isForce();
        RagReindexResponse response = ragService.startReindex(force);
        return ResponseEntity.ok(ApiResponse.success("Reindex started", response));
    }

    /**
     * Poll the progress of a reindex task.
     */
    @GetMapping("/reindex-status/{taskId}")
    public ResponseEntity<ApiResponse<RagReindexStatusResponse>> getReindexStatus(
            @AuthenticationPrincipal User user,
            @PathVariable String taskId) {
        RagReindexStatusResponse status = ragService.getReindexStatus(taskId);
        return ResponseEntity.ok(ApiResponse.success(status));
    }

    /**
     * Ask a medical/drug question against the RAG index.
     */
    @PostMapping("/ask")
    public ResponseEntity<ApiResponse<RagAskResponse>> ask(
            @AuthenticationPrincipal User user,
            @RequestBody RagAskRequest request) {
        if (request.getQuestion() == null || request.getQuestion().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("question is required"));
        }
        RagAskResponse response = ragService.ask(
                request.getQuestion().trim(), request.getTopK());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
