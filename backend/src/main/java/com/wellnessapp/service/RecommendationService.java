/**
 * @author Jia Qianrui
 */
package com.wellnessapp.service;

import com.wellnessapp.dto.RecommendationDTOs.*;
import com.wellnessapp.entity.Recommendation;
import com.wellnessapp.entity.User;
import com.wellnessapp.repository.RecommendationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Service for agentic AI recommendation generation.
 * Calls the Python agent service and stores results.
 *
 * @author WellnessApp Team
 */
@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    private final RecommendationRepository recommendationRepository;
    private final RestTemplate restTemplate;
    private final String agentBaseUrl;
    private final boolean agentEnabled;
    private final JwtUtilProvider jwtUtilProvider;

    public RecommendationService(
            RecommendationRepository recommendationRepository,
            @Value("${agent.python.base-url:http://localhost:5001}") String agentBaseUrl,
            @Value("${agent.python.enabled:true}") boolean agentEnabled,
            JwtUtilProvider jwtUtilProvider) {
        this.recommendationRepository = recommendationRepository;
        this.restTemplate = new RestTemplate();
        this.agentBaseUrl = agentBaseUrl;
        this.agentEnabled = agentEnabled;
        this.jwtUtilProvider = jwtUtilProvider;
    }

    /**
     * Trigger the agentic AI flow for the given user.
     */
    public RecommendationResponse triggerAgent(User user) {
        String jwtToken = jwtUtilProvider.generateToken(user.getUsername(), user.getId());

        String analysisSummary = null;
        String recommendationText = null;

        if (agentEnabled) {
            try {
                Map<String, Object> request = Map.of(
                        "userId", user.getId(),
                        "username", user.getUsername(),
                        "jwtToken", jwtToken,
                        "backendUrl", "http://localhost:8080"
                );

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

                ResponseEntity<Map> response = restTemplate.postForEntity(
                        agentBaseUrl + "/analyze", entity, Map.class);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    Map body = response.getBody();
                    analysisSummary = (String) body.get("analysisSummary");
                    List<String> recommendations =
                            (List<String>) body.get("recommendations");
                    if (recommendations != null && !recommendations.isEmpty()) {
                        recommendationText = String.join("\n\n", recommendations);
                    }
                }
            } catch (Exception e) {
                log.warn("Python agent unavailable, using fallback analysis: {}", e.getMessage());
            }
        }

        // Fallback if agent is unavailable
        if (recommendationText == null) {
            recommendationText = generateFallbackRecommendation(user.getUsername());
            analysisSummary = "Generated locally (Python agent was unavailable).";
        }

        // Save to database
        Recommendation rec = Recommendation.builder()
                .user(user)
                .recommendationText(recommendationText)
                .analysisSummary(analysisSummary)
                .generatedAt(LocalDateTime.now())
                .isRead(false)
                .build();
        rec = recommendationRepository.save(rec);

        return toResponse(rec);
    }

    /**
     * Get all recommendations for a user, ordered by most recent first.
     */
    public List<RecommendationResponse> getRecommendations(User user) {
        return recommendationRepository.findByUserIdOrderByGeneratedAtDesc(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Generate a basic fallback recommendation without the Python agent.
     */
    private String generateFallbackRecommendation(String username) {
        return String.format(
                "Hello %s!\n\n" +
                "Here are some general wellness tips:\n" +
                "1. Aim for 7-9 hours of sleep each night.\n" +
                "2. Exercise at least 150 minutes per week.\n" +
                "3. Stay hydrated — drink 2-3 liters of water daily.\n" +
                "4. Practice mindfulness or meditation for 5-10 minutes daily.\n\n" +
                "Log more health data to get personalized AI-powered insights!",
                username
        );
    }

    private RecommendationResponse toResponse(Recommendation rec) {
        return RecommendationResponse.builder()
                .id(rec.getId())
                .userId(rec.getUser().getId())
                .recommendationText(rec.getRecommendationText())
                .analysisSummary(rec.getAnalysisSummary())
                .generatedAt(rec.getGeneratedAt() != null ?
                        rec.getGeneratedAt().toString() : null)
                .isRead(rec.isRead())
                .build();
    }
}
