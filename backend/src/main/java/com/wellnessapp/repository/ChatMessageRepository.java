/**
 * @author Jia Qianrui
 * @author Zhang Xuhan
 */
package com.wellnessapp.repository;

import com.wellnessapp.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for ChatMessage entity.
 *
 * @author WellnessApp Team
 */
@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndCreatedAtBetween(
            Long userId, LocalDateTime startDateTime, LocalDateTime endDateTime);
}
