/**
 * @author Jia Qianrui
 * @author Cai Hanbo
 */
package com.wellnessapp.service;

import com.wellnessapp.dto.WellnessRecordDTOs.*;
import com.wellnessapp.entity.User;
import com.wellnessapp.entity.WellnessRecord;
import com.wellnessapp.repository.WellnessRecordRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Business logic for wellness record CRUD operations.
 * Ensures users can only access their own records.
 *
 * @author WellnessApp Team
 */
@Service
public class WellnessRecordService {

    private final WellnessRecordRepository recordRepository;

    public WellnessRecordService(WellnessRecordRepository recordRepository) {
        this.recordRepository = recordRepository;
    }

    /**
     * Get all wellness records for a user, ordered by date descending.
     */
    public List<WellnessRecordResponse> getAllRecords(User user) {
        return recordRepository.findByUserIdOrderByRecordDateDesc(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Get records within a date range (used by Python agent for trend analysis).
     */
    public List<WellnessRecordResponse> getRecordsInRange(User user, LocalDate start, LocalDate end) {
        return recordRepository.findByUserIdAndRecordDateBetweenOrderByRecordDateAsc(
                        user.getId(), start, end)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Create a new wellness record.
     */
    public WellnessRecordResponse createRecord(User user, WellnessRecordRequest request) {
        WellnessRecord record = WellnessRecord.builder()
                .user(user)
                .sleepHours(request.getSleepHours())
                .activityName(request.getActivityName())
                .activityDurationMinutes(request.getActivityDurationMinutes())
                .recordDate(parseRecordDate(request.getRecordDate()))
                .notes(request.getNotes())
                .build();

        record = recordRepository.save(record);
        return toResponse(record);
    }

    /**
     * Update an existing wellness record.
     */
    public WellnessRecordResponse updateRecord(User user, Long recordId, WellnessRecordRequest request) {
        WellnessRecord record = findOwnedRecord(user, recordId);

        record.setSleepHours(request.getSleepHours());
        record.setActivityName(request.getActivityName());
        record.setActivityDurationMinutes(request.getActivityDurationMinutes());
        record.setRecordDate(parseRecordDate(request.getRecordDate()));
        record.setNotes(request.getNotes());

        record = recordRepository.save(record);
        return toResponse(record);
    }

    /**
     * Delete a wellness record.
     */
    public void deleteRecord(User user, Long recordId) {
        WellnessRecord record = findOwnedRecord(user, recordId);
        recordRepository.delete(record);
    }

    /**
     * Find a record by ID and verify ownership.
     */
    private WellnessRecord findOwnedRecord(User user, Long recordId) {
        WellnessRecord record = recordRepository.findById(recordId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Record not found with id: " + recordId));

        if (!record.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("You can only modify your own records");
        }

        return record;
    }

    private WellnessRecordResponse toResponse(WellnessRecord record) {
        return WellnessRecordResponse.builder()
                .id(record.getId())
                .userId(record.getUser().getId())
                .sleepHours(record.getSleepHours())
                .activityName(record.getActivityName())
                .activityDurationMinutes(record.getActivityDurationMinutes())
                .recordDate(record.getRecordDate().toString())
                .notes(record.getNotes())
                .createdAt(record.getCreatedAt() != null ? record.getCreatedAt().toString() : null)
                .updatedAt(record.getUpdatedAt() != null ? record.getUpdatedAt().toString() : null)
                .build();
    }

    private LocalDate parseRecordDate(String recordDate) {
        try {
            return LocalDate.parse(recordDate);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Record date must be a valid date in YYYY-MM-DD format");
        }
    }
}
