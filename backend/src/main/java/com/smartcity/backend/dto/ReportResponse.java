package com.smartcity.backend.dto;

import com.smartcity.backend.enums.ReportCategory;
import com.smartcity.backend.enums.ReportPriority;
import com.smartcity.backend.enums.ReportStatus;
import com.smartcity.backend.model.Report;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Data
@Builder
public class ReportResponse {

    private String reportId;
    private Long userId;
    private String description;
    private double lat;
    private double lon;
    private ReportCategory category;

    // Status
    private ReportStatus status;

    // Priority & ownership
    private ReportPriority priority;
    private String prioritySetBy;

    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime unassessedAt;

    // Hours remaining in the 48h window (null when not UNASSESSED)
    private Long hoursRemainingInWindow;

    // Community votes
    private int stillVotes;
    private int fixedVotes;

    // AI analysis
    private double validationScore;
    private String validationReason;
    private int revalidationCount;

    // Image URL (stored in Firebase, only the URL lives in DB)
    private String imageUrl;

    public static ReportResponse from(Report r) {
        Long hoursRemaining = null;
        if (r.getStatus() == ReportStatus.UNASSESSED && r.getUnassessedAt() != null) {
            long elapsed = ChronoUnit.HOURS.between(r.getUnassessedAt(), LocalDateTime.now());
            hoursRemaining = Math.max(0, 48 - elapsed);
        }

        return ReportResponse.builder()
                .reportId(r.getReportId())
                .userId(r.getUserId())
                .description(r.getDescription())
                .lat(r.getLat())
                .lon(r.getLon())
                .category(r.getCategory())
                .status(r.getStatus())
                .priority(r.getPriority())
                .prioritySetBy(r.getPrioritySetBy())
                .createdAt(r.getCreatedAt())
                .resolvedAt(r.getResolvedAt())
                .unassessedAt(r.getUnassessedAt())
                .hoursRemainingInWindow(hoursRemaining)
                .stillVotes(r.getStillVotes())
                .fixedVotes(r.getFixedVotes())
                .validationScore(r.getValidationScore())
                .validationReason(r.getValidationReason())
                .revalidationCount(r.getRevalidationCount())
                .imageUrl(r.getImageUrl())
                .build();
    }
}
