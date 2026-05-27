package com.smartcity.backend.service;

import com.smartcity.backend.GeoUtil;
import com.smartcity.backend.dto.*;
import com.smartcity.backend.enums.ReportCategory;
import com.smartcity.backend.enums.ReportPriority;
import com.smartcity.backend.enums.ReportStatus;
import com.smartcity.backend.enums.VoteType;
import com.smartcity.backend.exception.ReportNotFoundException;
import com.smartcity.backend.exception.TooManyRequestsException;
import com.smartcity.backend.model.H3TokenAgg;
import com.smartcity.backend.model.Report;
import com.smartcity.backend.model.ReportH3;
import com.smartcity.backend.model.UserVote;
import com.smartcity.backend.repository.ReportRepository;
import com.smartcity.backend.repository.UserVoteRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final int    VOTE_THRESHOLD  = 5;
    private static final double VOTE_BOOST      = 0.1;
    private static final double VALID_THRESHOLD = 0.6;

    private final ReportRepository   reportRepository;
    private final UserVoteRepository userVoteRepository;
    private final H3ReportService h3ReportService;

    private final H3CoreService h3CoreService;
    private final AiService aiService;

    // =========================================================================
    // CREATE
    // =========================================================================

    @Transactional
    public ReportResponse createReport(Long userId, ReportCategory category,
                                       String description, double lat, double lon,
                                       List<String> imageUrls) {
        if (description == null || description.trim().length() < 20) {
            throw new IllegalArgumentException("Description must be at least 20 characters.");
        }

        // Enforce: at most 2 reports per user per 24 hours
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        if (reportRepository.countByUserIdAndCreatedAtAfter(userId, cutoff) >= 2) {
            throw new TooManyRequestsException(
                    "You can only submit 2 reports every 24 hours. Please try again later.");
        }

        Report report = Report.builder()
                .userId(userId)
                .category(category)
                .description(description.trim())
                .lat(lat)
                .lon(lon)
                .status(ReportStatus.UNASSESSED)
                .priority(ReportPriority.LOW)
                .prioritySetBy("AI")
                .unassessedAt(LocalDateTime.now())
                .imageUrls(imageUrls != null ? imageUrls : new ArrayList<>())
                .build();

        Report saved = reportRepository.save(report);
        h3ReportService.InsertReportH3(report);

        // Fire AI analysis in background — user already has the response
        triggerAiAnalysis(saved.getReportId());

        return ReportResponse.from(saved);
    }

    // =========================================================================
    // READ
    // =========================================================================
    public List<ReportSummary> getAllReportsSummaryInViewPort(double northLat, double northLng, double southLat, double southLng, int zoom) {
        List<Long> cells = h3CoreService.getCellsInViewport(northLat, northLng, southLat, southLng, zoom);
        List<H3TokenAgg> temp = h3ReportService.getReportAgg(cells);
        return temp.stream().map(e-> new ReportSummary(GeoUtil.fromXYZToLatLng(e.getX(),e.getY(),e.getZ()), e.getCount())).toList();
    }
    public List<ReportResponse> getAllReportsInViewPort(double northLat, double northLng, double southLat, double southLng, int zoom) {
        List<Long> cells = h3CoreService.getCellsInViewport(northLat, northLng, southLat, southLng, zoom);
        return h3ReportService.getAllReports(cells).stream().map(ReportResponse::from).toList();
    }


    public List<ReportResponse> getAllReports() {
        return reportRepository.findAllByOrderByCreatedAtDesc()
                .stream().map(ReportResponse::from).collect(Collectors.toList());
    }

    public List<ReportResponse> getUserReports(Long userId) {
        return reportRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(ReportResponse::from).collect(Collectors.toList());
    }

    public ReportResponse getReportById(String reportId) {
        return ReportResponse.from(findOrThrow(reportId));
    }

    // =========================================================================
    // VOTE
    // =========================================================================

    @Transactional
    public ReportResponse voteReport(Long userId, String reportId, VoteType voteType) {
        Report report = findOrThrow(reportId);

        // Fixed votes are meaningless on an unconfirmed report
        if (report.getStatus() == ReportStatus.UNASSESSED && voteType == VoteType.Fixed) {
            throw new IllegalArgumentException(
                    "Cannot vote Fixed on an UNASSESSED report. Use Still to support it.");
        }

        // Still votes make no sense on closed reports
        if (voteType == VoteType.Still &&
                (report.getStatus() == ReportStatus.RESOLVED ||
                 report.getStatus() == ReportStatus.REJECTED)) {
            throw new IllegalArgumentException(
                    "Cannot vote Still on a " + report.getStatus() + " report.");
        }

        Optional<UserVote> existingOpt = userVoteRepository.findByUserIdAndReportId(userId, reportId);

        if (existingOpt.isPresent()) {
            UserVote existing = existingOpt.get();

            // Enforce 24-hour cooldown between vote changes
            long hoursSince = ChronoUnit.HOURS.between(existing.getVotedAt(), LocalDateTime.now());
            if (hoursSince < 24) {
                long hoursLeft = 24 - hoursSince;
                throw new TooManyRequestsException(
                        "You can change your vote in " + hoursLeft + " hour(s).");
            }

            // Same vote type — nothing to change
            if (existing.getVoteType() == voteType) {
                return ReportResponse.from(report);
            }

            // Swap the counts: undo old vote, apply new vote
            applyVoteDelta(report, existing.getVoteType(), -1);
            applyVoteDelta(report, voteType, +1);

            existing.setVoteType(voteType);
            existing.setVotedAt(LocalDateTime.now());
            userVoteRepository.save(existing);

        } else {
            // First time voting on this report
            applyVoteDelta(report, voteType, +1);
            userVoteRepository.save(UserVote.builder()
                    .userId(userId)
                    .reportId(reportId)
                    .voteType(voteType)
                    .votedAt(LocalDateTime.now())
                    .build());
        }

        // Check if community votes push an UNASSESSED report over the threshold
        if (report.getStatus() == ReportStatus.UNASSESSED) {
            double boostedScore = report.getValidationScore()
                    + (report.getStillVotes() * VOTE_BOOST);

            if (boostedScore >= VALID_THRESHOLD) {
                log.info("Report {} hit vote threshold (stillVotes={}), triggering re-validation",
                        reportId, report.getStillVotes());
                triggerAiAnalysis(reportId);
            }
        }

        return ReportResponse.from(reportRepository.save(report));
    }

    /** Adjusts a vote counter by +1 or -1, clamping to zero. */
    private void applyVoteDelta(Report report, VoteType type, int delta) {
        if (type == VoteType.Still) {
            report.setStillVotes(Math.max(0, report.getStillVotes() + delta));
        } else {
            report.setFixedVotes(Math.max(0, report.getFixedVotes() + delta));
        }
    }

    // =========================================================================
    // ADMIN — update
    // =========================================================================

    @Transactional
    public ReportResponse updateReport(String reportId, UpdateReportRequest req) {
        Report report = findOrThrow(reportId);

        if (req.isResetAiControl()) {
            // Admin hands priority ownership back to AI
            report.setPrioritySetBy("AI");
        } else {
            if (req.getStatus() != null) {
                report.setStatus(req.getStatus());
                if (req.getStatus() == ReportStatus.RESOLVED && report.getResolvedAt() == null) {
                    report.setResolvedAt(LocalDateTime.now());
                }
            }
            if (req.getPriority() != null) {
                report.setPriority(req.getPriority());
                report.setPrioritySetBy("ADMIN");
            }
        }

        return ReportResponse.from(reportRepository.save(report));
    }

    // =========================================================================
    // IMAGE UPDATE (user during 48h window, or admin any time)
    // =========================================================================

    @Transactional
    public ReportResponse updateReportImages(String reportId, Long requestingUserId,
                                             boolean isAdmin, List<String> newImageUrls) {
        Report report = findOrThrow(reportId);

        if (!isAdmin) {
            if (!report.getUserId().equals(requestingUserId)) {
                throw new IllegalArgumentException("You can only update your own report's images.");
            }
            if (report.getStatus() != ReportStatus.UNASSESSED) {
                throw new IllegalArgumentException(
                        "Images can only be updated while the report is UNASSESSED.");
            }
        }

        report.getImageUrls().clear();
        report.getImageUrls().addAll(newImageUrls);
        return ReportResponse.from(reportRepository.save(report));
    }

    // =========================================================================
    // ADMIN — filter & stats
    // =========================================================================

    public List<ReportResponse> filterReports(ReportStatus status, ReportCategory category,
                                              LocalDateTime startDate, LocalDateTime endDate) {
        return reportRepository.findWithFilters(status, category, startDate, endDate)
                .stream().map(ReportResponse::from).collect(Collectors.toList());
    }

    public AdminStatsResponse getAdminStats() {
        long total = reportRepository.count();

        Map<String, Long> byCategory = new LinkedHashMap<>();
        for (ReportCategory cat : ReportCategory.values()) {
            byCategory.put(cat.name(), reportRepository.countByCategory(cat));
        }

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (ReportStatus st : ReportStatus.values()) {
            byStatus.put(st.name(), reportRepository.countByStatus(st));
        }

        long resolved = byStatus.getOrDefault(ReportStatus.RESOLVED.name(), 0L);
        double resolutionRate = total > 0 ? (resolved * 100.0) / total : 0.0;
        Double avgHours = reportRepository.findAverageResolutionHours();

        return AdminStatsResponse.builder()
                .totalReports(total)
                .reportsByCategory(byCategory)
                .reportsByStatus(byStatus)
                .resolutionRate(Math.round(resolutionRate * 10.0) / 10.0)
                .averageResolutionHours(avgHours != null ? Math.round(avgHours * 10.0) / 10.0 : 0.0)
                .build();
    }

    // =========================================================================
    // AI INTEGRATION — async trigger
    // =========================================================================

    /**
     * Runs in a background thread (@Async) so the HTTP response is never delayed.
     * @Transactional here starts a fresh transaction in the async thread —
     * self-calling applyAiResult() directly keeps everything in one transaction
     * and avoids the Spring proxy self-invocation problem.
     *
     * When the real Python service is ready, only AiService.analyzeReport() changes.
     */
    @Async
    @Transactional
    public void triggerAiAnalysis(String reportId) {
        Report report = reportRepository.findById(reportId).orElse(null);
        if (report == null) return;

        // Skip if already past the UNASSESSED stage (admin may have acted)
        if (report.getStatus() != ReportStatus.UNASSESSED) {
            log.info("Skipping AI analysis for report {} — status is already {}",
                    reportId, report.getStatus());
            return;
        }

        try {
            String firstImageUrl = report.getImageUrls().isEmpty()
                    ? null : report.getImageUrls().get(0);

            AiAnalysisResult result = aiService.analyzeReport(
                    report.getCategory(),
                    report.getDescription(),
                    firstImageUrl,
                    report.getLat(),
                    report.getLon(),
                    report.getStillVotes()
            );

            // Update AI fields
            report.setValidationScore(result.getConfidence());
            report.setValidationReason(result.getReason());
            report.setRevalidationCount(report.getRevalidationCount() + 1);

            if (result.isValid()) {
                // Status: AI only moves UNASSESSED → PENDING, never overrides further
                report.setStatus(ReportStatus.PENDING);

                // Priority: only update if admin hasn't claimed ownership
                if ("AI".equals(report.getPrioritySetBy())) {
                    report.setPriority(result.getPriority());
                }
            }
            // If not valid: stays UNASSESSED — scheduler closes it after 48h

            reportRepository.save(report);
            log.info("AI applied to report {}: valid={}, confidence={}, priority={}",
                    reportId, result.isValid(), result.getConfidence(), result.getPriority());

        } catch (Exception e) {
            log.error("AI analysis failed for report {}: {}", reportId, e.getMessage());
        }
    }

    // =========================================================================
    // HELPER
    // =========================================================================

    private Report findOrThrow(String reportId) {
        return reportRepository.findById(reportId)
                .orElseThrow(() -> new ReportNotFoundException(
                        "Report not found with id: " + reportId));
    }


}
