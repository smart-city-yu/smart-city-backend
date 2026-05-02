package com.smartcity.backend.service;

import com.smartcity.backend.dto.AiAnalysisResult;
import com.smartcity.backend.enums.ReportCategory;
import com.smartcity.backend.enums.ReportPriority;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * AI analysis service.
 *
 * Currently a rule-based stub — always returns valid = true with a
 * confidence of 0.75 and a priority derived from category + description keywords.
 *
 * When the Python FastAPI AI service is ready, replace the body of
 * analyzeReport() with an HTTP call to POST /analyze and deserialize
 * the response into AiAnalysisResult. Nothing else in the codebase changes.
 */
@Service
public class AiService {

    private static final double STUB_CONFIDENCE = 0.75;
    private static final String STUB_REASON = "Auto-validated (AI stub)";

    public AiAnalysisResult analyzeReport(ReportCategory category,
                                          String description,
                                          String imageUrl,
                                          double lat,
                                          double lon,
                                          int stillVotes) {
        ReportPriority priority = assignPriority(category, description);

        return AiAnalysisResult.builder()
                .valid(true)
                .confidence(STUB_CONFIDENCE)
                .reason(STUB_REASON)
                .priority(priority)
                .build();
    }

    // -------------------------------------------------------------------------
    // Rule-based priority — replaces this entirely when real AI is connected
    // -------------------------------------------------------------------------

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
}
