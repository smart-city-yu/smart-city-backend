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
 * Calls the Python FastAPI AI service:
 *   POST /analyze  — LLM text validation  (always called)
 *   POST /detect   — YOLO image detection (called only when image is present)
 *
 * Falls back to rule-based stub if the Python service is unreachable.
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
                                          List<String> imageUrls,
                                          double lat,
                                          double lon,
                                          int stillVotes,
                                          boolean isPredefined) {
        try {
            String imageDescription = null;

            // Step 1 — Image analysis (send ALL images to Nemotron at once)
            boolean nemotronDetected  = false;
            String  nemotronCategory  = "other";
            double  nemotronConfidence = 0.0;

            if (imageUrls != null && !imageUrls.isEmpty()) {
                try {
                    DetectResult detect = callDetect(imageUrls);
                    imageDescription   = detect.imageDescription();
                    nemotronDetected   = detect.detected();
                    nemotronCategory   = detect.category();
                    nemotronConfidence = detect.confidence();
                    log.info("Image analysis ({} image(s)) → detected={} category={} confidence={} description='{}'",
                            imageUrls.size(), detect.detected(), detect.category(), detect.confidence(), imageDescription);
                } catch (Exception e) {
                    log.warn("Image /detect failed ({}), proceeding without image context.", e.getMessage());
                }
            }

            // Step 2 — LLM validates text + uses image description for smart priority
            String firstImageUrl = (imageUrls != null && !imageUrls.isEmpty()) ? imageUrls.get(0) : null;
            int imageCount = (imageUrls != null) ? imageUrls.size() : 0;
            AiAnalysisResult llmResult = callAnalyze(
                    category, description, firstImageUrl, lat, lon, stillVotes, isPredefined,
                    imageDescription, imageCount, nemotronDetected, nemotronCategory, nemotronConfidence);

            log.info("Final result → valid={} confidence={} priority={}",
                    llmResult.isValid(), llmResult.getConfidence(), llmResult.getPriority());
            return llmResult;

        } catch (Exception e) {
            log.warn("Python AI service unavailable ({}), using rule-based fallback.", e.getMessage());
            return fallback(category, description);
        }
    }

    // -------------------------------------------------------------------------
    // HTTP call — POST /analyze  (LLM text validation + smart priority)
    // Now receives image_description from Nemotron for full-context decision
    // -------------------------------------------------------------------------

    private AiAnalysisResult callAnalyze(ReportCategory category,
                                         String description,
                                         String imageUrl,
                                         double lat,
                                         double lon,
                                         int stillVotes,
                                         boolean isPredefined,
                                         String imageDescription,
                                         int imageCount,
                                         boolean nemotronDetected,
                                         String nemotronCategory,
                                         double nemotronConfidence) throws Exception {

        String body = objectMapper.writeValueAsString(new AnalyzeRequest(
                category.name(), description, imageUrl, lat, lon, stillVotes, isPredefined,
                imageDescription, imageCount, nemotronDetected, nemotronCategory, nemotronConfidence
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(aiServiceUrl + "/analyze"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(90))
                .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200)
            throw new RuntimeException("AI /analyze returned HTTP " + response.statusCode());

        JsonNode json = objectMapper.readTree(response.body());

        boolean valid       = json.get("valid").asBoolean();
        double  confidence  = json.get("confidence").asDouble();
        String  reason      = json.get("reason").asText();
        String  priorityStr = json.get("priority").asText("MEDIUM");

        ReportPriority priority;
        try {
            priority = ReportPriority.valueOf(priorityStr.toUpperCase());
        } catch (IllegalArgumentException ex) {
            priority = ReportPriority.MEDIUM;
        }

        log.info("LLM /analyze → valid={} confidence={} priority={} reason={}",
                valid, confidence, priority, reason);

        return AiAnalysisResult.builder()
                .valid(valid)
                .confidence(confidence)
                .reason(reason)
                .priority(priority)
                .build();
    }

    // -------------------------------------------------------------------------
    // HTTP call — POST /detect  (image analysis — returns description for LLM)
    // -------------------------------------------------------------------------

    private DetectResult callDetect(List<String> imageUrls) throws Exception {

        String body = objectMapper.writeValueAsString(new DetectRequest(imageUrls));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(aiServiceUrl + "/detect"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(120))   // Nemotron can take 60–90s for large images
                .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200)
            throw new RuntimeException("AI /detect returned HTTP " + response.statusCode());

        JsonNode json = objectMapper.readTree(response.body());

        boolean detected         = json.get("detected").asBoolean();
        String  detCategory      = json.get("category").asText("other");
        double  confidence       = json.get("confidence").asDouble();
        String  imageDescription = json.has("image_description")
                ? json.get("image_description").asText("")
                : "";

        return new DetectResult(detected, detCategory, confidence, imageDescription);
    }

    // -------------------------------------------------------------------------
    // Rule-based fallback (used only when Python service is completely down)
    // -------------------------------------------------------------------------

    private AiAnalysisResult fallback(ReportCategory category, String description) {
        return AiAnalysisResult.builder()
                .valid(true)
                .confidence(0.55)   // conservative — real AI wasn't available to validate
                .reason("Pending AI review (service temporarily unavailable)")
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
    // Inner records
    // -------------------------------------------------------------------------

    private record AnalyzeRequest(
            String category,
            String description,
            String image_url,
            double lat,
            double lon,
            int still_votes,
            boolean is_predefined,
            String image_description,
            int image_count,
            boolean nemotron_detected,
            String nemotron_category,
            double nemotron_confidence
    ) {}

    private record DetectRequest(List<String> image_urls) {}

    private record DetectResult(boolean detected, String category, double confidence, String imageDescription) {}
}
