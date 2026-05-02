package com.smartcity.backend.dto;

import com.smartcity.backend.enums.ReportPriority;
import lombok.Builder;
import lombok.Data;

/**
 * Represents the response from the AI analysis service.
 * Currently populated by AiService stub with static values.
 * When the real Python AI service is connected, only AiService changes —
 * this contract stays the same.
 */
@Data
@Builder
public class AiAnalysisResult {
    private boolean valid;
    private double confidence;   // 0.0 → 1.0
    private String reason;       // human-readable explanation
    private ReportPriority priority;
}
