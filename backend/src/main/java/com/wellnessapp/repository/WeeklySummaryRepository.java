/**
 * @author Liu Zhuocheng
 */
package com.wellnessapp.repository;

import com.wellnessapp.entity.WeeklySummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for generated weekly summaries.
 *
 * @author Liu Zhuocheng
 */
@Repository
public interface WeeklySummaryRepository extends JpaRepository<WeeklySummary, Long> {

    List<WeeklySummary> findByUserIdOrderByGeneratedAtDesc(Long userId);
}
