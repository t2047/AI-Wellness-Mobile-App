/**
 * @author Jia Qianrui
 */
package com.wellnessapp.repository;

import com.wellnessapp.entity.WellnessRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository for WellnessRecord entity.
 *
 * @author WellnessApp Team
 */
@Repository
public interface WellnessRecordRepository extends JpaRepository<WellnessRecord, Long> {

    List<WellnessRecord> findByUserIdOrderByRecordDateDesc(Long userId);

    List<WellnessRecord> findByUserIdAndRecordDateBetweenOrderByRecordDateAsc(
            Long userId, LocalDate startDate, LocalDate endDate);

    List<WellnessRecord> findByUserIdOrderByRecordDateAsc(Long userId);
}
