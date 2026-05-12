package com.smartcity.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcity.backend.dto.AiAnalysisResult;
import com.smartcity.backend.enums.ReportCategory;
import com.smartcity.backend.enums.ReportPriority;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Calls the Python FastAPI AI service at POST /analyze.
 * Falls back to the rule-based stub if the Python service is unreachable.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    @Value("${ai.service.url:http://localhost:8000}")
    private String aiServiceUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // -------------------------------------------------------------------------
    // Main entry point called by ReportService
    // -------------------------------------------------------------------------

    public AiAnalysisResult analyzeReport(ReportCategory category,
                                          String description,
                                          String imageUrl,
                                          double lat,
                                          double lon,
                                          int stillVotes) {
        try {
            return callPythonService(category, description, imageUrl, lat, lon, stillVotes);
        } catch (Exception e) {
            log.warn("Python AI service unavailable ({}), using rule-based fallback.", e.getMessage());
            return fallback(category, description);
        }
    }

    // -------------------------------------------------------------------------
    // HTTP call to Python FastAPI /analyze
    // -------------------------------------------------------------------------

    private AiAnalysisResult callPythonService(ReportCategory category,
                                               String description,
                                               String imageUrl,
                                               double lat,
                                               double lon,
                                               int stillVotes) throws Exception {

        String body = objectMapper.writeValueAsString(new PythonRequest(
                category.name(), description, imageUrl, lat, lon, stillVotes
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(aiServiceUrl + "/analyze"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(60))   // AI call may take a moment
                .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("AI service returned HTTP " + response.statusCode());
        }

        JsonNode json = objectMapper.readTree(response.body());

        boolean valid      = json.get("valid").asBoolean();
        double  confidence = json.get("confidence").asDouble();
        String  reason     = json.get("reason").asText();
        String  priorityStr = json.get("priority").asText("LOW");

        ReportPriority priority;
        try {
            priority = ReportPriority.valueOf(priorityStr.toUpperCase());
        } catch (IllegalArgumentException ex) {
            priority = ReportPriority.LOW;
        }

        log.info("AI result → valid={} confidence={} priority={} reason={}",
                valid, confidence, priority, reason);

        return AiAnalysisResult.builder()
                .valid(valid)
                .confidence(confidence)
                .reason(reason)
                .priority(priority)
                .build();
    }

    // -------------------------------------------------------------------------
    // Rule-based fallback (original stub — used only when Python is down)
    // -------------------------------------------------------------------------

    private AiAnalysisResult fallback(ReportCategory category, String description) {
        return AiAnalysisResult.builder()
                .valid(true)
                .confidence(0.75)
                .reason("Auto-validated (AI service unavailable — rule-based fallback)")
                .priority(assignPriority(category, description))
                .build();
    }

    private ReportPriority assignPriority(ReportCategory category, String description) {
        String lower = description.toLowerCase();

        List<String> criticalKeywords = Arrays.asList(
                "collapse", "collapsed", "flood", "flooding", "fire", "explosion",
                "dangerous", "emergency", "fatal", "accident", "blocked completely",
                "major hazard", "sinkhole", "gas leak"
        );
        for (String kw : criticalKeywords) {
            if (lower.contains(kw)) return ReportPriority.CRITICAL;
        }

        List<String> highKeywords = Arrays.asList(
                "deep", "large", "severe", "serious", "unsafe", "broken",
                "falling", "leaking", "major", "damage", "cracked", "open manhole"
        );
        boolean hasHighKeyword = highKeywords.stream().anyMatch(lower::contains);

        ReportPriority baseline = switch (category) {
            case manhole, treeInRoad, brokenRoad -> ReportPriority.HIGH;
            case pothole, lamppost               -> ReportPriority.MEDIUM;
            case unpavedStreet, speedBump        -> ReportPriority.LOW;
            case other                           -> ReportPriority.MEDIUM;
        };

        if (hasHighKeyword) {
            return switch (baseline) {
                case LOW    -> ReportPriority.MEDIUM;
                case MEDIUM -> ReportPriority.HIGH;
                default     -> baseline;
            };
        }

        return baseline;
    }

    // -------------------------------------------------------------------------
    // Inner DTO for the Python request body
    // -------------------------------------------------------------------------

    private record PythonRequest(
            String category,
            String description,
            String image_url,
            double lat,
            double lon,
            int still_votes
    ) {}
}
