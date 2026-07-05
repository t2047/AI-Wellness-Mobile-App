package com.wellnessapp.repository;

import com.wellnessapp.entity.Recommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Recommendation entity.
 *
 * @author WellnessApp Team
 */
@Repository
public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {

    List<Recommendation> findByUserIdOrderByGeneratedAtDesc(Long userId);

    long countByUserId(Long userId);

    @Query("select count(r) from Recommendation r where r.user.id = :userId and r.isRead = false")
    long countUnreadByUserId(@Param("userId") Long userId);
}
