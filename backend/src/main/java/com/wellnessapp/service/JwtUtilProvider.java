/**
 * @author Jia Qianrui
 */
package com.wellnessapp.service;

import com.wellnessapp.security.JwtUtil;
import org.springframework.stereotype.Component;

/**
 * Wrapper to expose JwtUtil token generation to services.
 * Required because JwtUtil is in the security package
 * and services need to generate tokens for the Python agent.
 *
 * @author WellnessApp Team
 */
@Component
public class JwtUtilProvider {

    private final JwtUtil jwtUtil;

    public JwtUtilProvider(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    public String generateToken(String username, Long userId) {
        return jwtUtil.generateToken(username, userId);
    }
}
