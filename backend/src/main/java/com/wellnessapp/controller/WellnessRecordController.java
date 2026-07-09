/**
 * @author Jia Qianrui
 */
package com.wellnessapp.controller;

import com.wellnessapp.dto.ApiResponse;
import com.wellnessapp.dto.WellnessRecordDTOs.*;
import com.wellnessapp.entity.User;
import com.wellnessapp.service.WellnessRecordService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for wellness record CRUD operations.
 * All operations are scoped to the authenticated user.
 *
 * @author WellnessApp Team
 */
@RestController
@RequestMapping("/api/wellness-records")
public class WellnessRecordController {

    private final WellnessRecordService recordService;

    public WellnessRecordController(WellnessRecordService recordService) {
        this.recordService = recordService;
    }

    /**
     * Get all wellness records for the authenticated user.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<WellnessRecordResponse>>> getAll(
            @AuthenticationPrincipal User user) {
        List<WellnessRecordResponse> records = recordService.getAllRecords(user);
        return ResponseEntity.ok(ApiResponse.success(records));
    }

    /**
     * Create a new wellness record.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<WellnessRecordResponse>> create(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody WellnessRecordRequest request) {
        WellnessRecordResponse record = recordService.createRecord(user, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Record created successfully", record));
    }

    /**
     * Update an existing wellness record.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<WellnessRecordResponse>> update(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @Valid @RequestBody WellnessRecordRequest request) {
        WellnessRecordResponse record = recordService.updateRecord(user, id, request);
        return ResponseEntity.ok(ApiResponse.success("Record updated successfully", record));
    }

    /**
     * Delete a wellness record.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        recordService.deleteRecord(user, id);
        return ResponseEntity.ok(ApiResponse.success("Record deleted successfully", null));
    }
}
