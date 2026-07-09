/**
 * @author Jia Qianrui
 */
package com.wellnessapp.service;

import com.wellnessapp.dto.AuthDTOs.*;
import com.wellnessapp.entity.User;
import com.wellnessapp.repository.UserRepository;
import com.wellnessapp.security.JwtUtil;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Handles user registration and login.
 *
 * @author WellnessApp Team
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    /**
     * Register a new user account.
     *
     * @param request Registration details
     * @return AuthResponse with JWT token
     */
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username is already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email is already registered");
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build();

        user = userRepository.save(user);

        String token = jwtUtil.generateToken(user.getUsername(), user.getId());
        return AuthResponse.of(token, user.getUsername(), user.getId());
    }

    /**
     * Authenticate a user with username and password.
     *
     * @param request Login credentials
     * @return AuthResponse with JWT token
     */
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() ->
                        new BadCredentialsException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid username or password");
        }

        String token = jwtUtil.generateToken(user.getUsername(), user.getId());
        return AuthResponse.of(token, user.getUsername(), user.getId());
    }
}
