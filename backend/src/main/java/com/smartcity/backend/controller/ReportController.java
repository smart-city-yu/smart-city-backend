package com.smartcity.backend.controller;

import com.smartcity.backend.dto.AdminStatsResponse;
import com.smartcity.backend.dto.ReportResponse;
import com.smartcity.backend.dto.UpdateReportRequest;
import com.smartcity.backend.enums.ReportCategory;
import com.smartcity.backend.enums.ReportStatus;
import com.smartcity.backend.enums.VoteType;
import com.smartcity.backend.enums.Role;
import com.smartcity.backend.model.User;
import com.smartcity.backend.service.CloudinaryService;
import com.smartcity.backend.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService      reportService;
    private final CloudinaryService  cloudinaryService;

    // -------------------------------------------------------------------------
    // POST /api/report/create  (multipart/form-data)
    // -------------------------------------------------------------------------
    @PostMapping(value = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReportResponse> createReport(
            @AuthenticationPrincipal User currentUser,
            @RequestParam("category")    ReportCategory category,
            @RequestParam("description") String description,
            @RequestParam("lat")         double lat,
            @RequestParam("lon")         double lon,
            @RequestParam(value = "images", required = false) List<MultipartFile> images
    ) {
        List<String> imageUrls = cloudinaryService.uploadImages(images, "reports");
        ReportResponse response = reportService.createReport(
                currentUser.getId(), category, description, lat, lon, imageUrls
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
            @AuthenticationPrincipal User currentUser,
            @RequestParam("reportId") String reportId,
            @RequestParam("voteType") VoteType voteType
    ) {
        return ResponseEntity.ok(reportService.voteReport(currentUser.getId(), reportId, voteType));
    }

    // -------------------------------------------------------------------------
    // PUT /api/report/{id}/images
    // User replaces their own report images during the 48h UNASSESSED window
    // -------------------------------------------------------------------------
    @PutMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReportResponse> updateImages(
            @AuthenticationPrincipal User currentUser,
            @PathVariable String id,
            @RequestParam("images") List<MultipartFile> images
    ) {
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;
        List<String> newUrls = cloudinaryService.uploadImages(images, "reports");
        return ResponseEntity.ok(
                reportService.updateReportImages(id, currentUser.getId(), isAdmin, newUrls)
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
