package com.smartcity.backend.model;

import com.smartcity.backend.enums.ReportCategory;
import com.smartcity.backend.enums.ReportPriority;
import com.smartcity.backend.enums.ReportStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "report")
@Entity
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String reportId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lon;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportCategory category;

    // --- Status & Priority ---

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportStatus status;

    @Enumerated(EnumType.STRING)
    private ReportPriority priority;

    // "AI" or "ADMIN" — whoever last set this field owns it
    // Only priority needs tracking — status conflict is handled by checking
    // the current status value (AI only ever moves UNASSESSED → PENDING)
    @Column(nullable = false)
    @Builder.Default
    private String prioritySetBy = "AI";

    // --- Timestamps ---

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime resolvedAt;

    // When the 48h UNASSESSED window started
    private LocalDateTime unassessedAt;

    // --- Community voting ---

    @Column(nullable = false)
    @Builder.Default
    private int stillVotes = 0;

    @Column(nullable = false)
    @Builder.Default
    private int fixedVotes = 0;

    // --- AI analysis fields ---
    // Populated by the real AI service in future.
    // The stub fills them with static values for now.

    @Column(nullable = false)
    @Builder.Default
    private double validationScore = 0.0;

    private String validationReason;

    @Column(nullable = false)
    @Builder.Default
    private int revalidationCount = 0;

    // --- Image ---
    // URL pointing to Firebase Storage (upload handled by Flutter client)
    private String imageUrl;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = ReportStatus.UNASSESSED;
        }
    }
}
