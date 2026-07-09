/**
 * @author Tao Yuchen
 */
package com.wellnessapp.repository;

import com.wellnessapp.entity.UserModelConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for per-user AI model configurations.
 *
 * @author WellnessApp Team
 */
@Repository
public interface UserModelConfigRepository extends JpaRepository<UserModelConfig, Long> {

    List<UserModelConfig> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<UserModelConfig> findByUserIdAndProviderName(Long userId, String providerName);

    Optional<UserModelConfig> findByUserIdAndIsActiveTrue(Long userId);

    List<UserModelConfig> findByUserIdAndIsActiveTrueAndProviderName(Long userId, String providerName);
}
