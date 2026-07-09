/**
 * @author Jia Qianrui
 */
package com.wellnessapp.controller;

import com.wellnessapp.dto.ApiResponse;
import com.wellnessapp.dto.ChatDTOs.*;
import com.wellnessapp.entity.User;
import com.wellnessapp.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for chatbot interactions.
 *
 * @author WellnessApp Team
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * Send a message to the chatbot and get a response.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ChatResponse>> sendMessage(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ChatRequest request) {
        ChatResponse response = chatService.processMessage(user, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get the chat history for the authenticated user.
     */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<ChatHistoryResponse>>> getHistory(
            @AuthenticationPrincipal User user) {
        List<ChatHistoryResponse> history = chatService.getHistory(user);
        return ResponseEntity.ok(ApiResponse.success(history));
    }
}
