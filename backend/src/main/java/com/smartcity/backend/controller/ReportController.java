package com.smartcity.backend.controller;

import com.smartcity.backend.dto.AdminStatsResponse;
import com.smartcity.backend.dto.ReportResponse;
import com.smartcity.backend.dto.UpdateReportRequest;
import com.smartcity.backend.enums.ReportCategory;
import com.smartcity.backend.enums.ReportStatus;
import com.smartcity.backend.enums.VoteType;
import com.smartcity.backend.enums.Role;
import com.smartcity.backend.model.User;
import com.smartcity.backend.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    // -------------------------------------------------------------------------
    // POST /api/report/create
    // -------------------------------------------------------------------------
    @PostMapping("/create")
    public ResponseEntity<ReportResponse> createReport(
            @AuthenticationPrincipal User currentUser,
            @RequestParam("category") ReportCategory category,
            @RequestParam("description") String description,
            @RequestParam("lat") double lat,
            @RequestParam("lon") double lon,
            @RequestParam(value = "imageUrl", required = false) String imageUrl
    ) {
        ReportResponse response = reportService.createReport(
                currentUser.getId(), category, description, lat, lon, imageUrl
        );
        return ResponseEntity.status(201).body(response);
    }

    // -------------------------------------------------------------------------
    // GET /api/report/all
    // -------------------------------------------------------------------------
    @GetMapping("/all")
    public ResponseEntity<List<ReportResponse>> getAllReports() {
        return ResponseEntity.ok(reportService.getAllReports());
    }

    // -------------------------------------------------------------------------
    // GET /api/report/user  — current user's own reports
    // -------------------------------------------------------------------------
    @GetMapping("/user")
    public ResponseEntity<List<ReportResponse>> getMyReports(
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(reportService.getUserReports(currentUser.getId()));
    }

    // -------------------------------------------------------------------------
    // GET /api/report/{id}
    // -------------------------------------------------------------------------
    @GetMapping("/{id}")
    public ResponseEntity<ReportResponse> getReportById(@PathVariable String id) {
        return ResponseEntity.ok(reportService.getReportById(id));
    }

    // -------------------------------------------------------------------------
    // POST /api/report/vote
    // -------------------------------------------------------------------------
    @PostMapping("/vote")
    public ResponseEntity<ReportResponse> voteReport(
            @RequestParam("reportId") String reportId,
            @RequestParam("voteType") VoteType voteType
    ) {
        return ResponseEntity.ok(reportService.voteReport(reportId, voteType));
    }

    // -------------------------------------------------------------------------
    // PUT /api/report/{id}/image
    // User updates their own report image during the 48h UNASSESSED window
    // -------------------------------------------------------------------------
    @PutMapping("/{id}/image")
    public ResponseEntity<ReportResponse> updateImage(
            @AuthenticationPrincipal User currentUser,
            @PathVariable String id,
            @RequestParam("imageUrl") String imageUrl
    ) {
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;
        return ResponseEntity.ok(
                reportService.updateReportImage(id, currentUser.getId(), isAdmin, imageUrl)
        );
    }

    // =========================================================================
    // ADMIN ENDPOINTS  — requires ROLE_ADMIN (enforced in SecurityConfig)
    // =========================================================================

    // GET /api/report/admin/stats
    @GetMapping("/admin/stats")
    public ResponseEntity<AdminStatsResponse> getAdminStats() {
        return ResponseEntity.ok(reportService.getAdminStats());
    }

    // PUT /api/report/admin/{id}  — update status / priority / reset AI control
    @PutMapping("/admin/{id}")
    public ResponseEntity<ReportResponse> updateReport(
            @PathVariable String id,
            @RequestBody UpdateReportRequest request
    ) {
        return ResponseEntity.ok(reportService.updateReport(id, request));
    }

    // GET /api/report/admin/filter
    @GetMapping("/admin/filter")
    public ResponseEntity<List<ReportResponse>> filterReports(
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false) ReportCategory category,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate
    ) {
        return ResponseEntity.ok(
                reportService.filterReports(status, category, startDate, endDate)
        );
    }
}
