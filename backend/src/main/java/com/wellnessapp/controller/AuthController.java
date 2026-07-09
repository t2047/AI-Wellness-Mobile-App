/**
 * @author Jia Qianrui
 */
package com.wellnessapp.controller;

import com.wellnessapp.dto.ApiResponse;
import com.wellnessapp.dto.AuthDTOs.*;
import com.wellnessapp.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for authentication (register and login).
 *
 * @author WellnessApp Team
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Register a new user.
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        AuthResponse auth = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("User registered successfully", auth));
    }

    /**
     * Login with username and password. Returns JWT token.
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        AuthResponse auth = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", auth));
    }
}
