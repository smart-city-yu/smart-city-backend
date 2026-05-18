package com.smartcity.backend.dto;

import com.uber.h3core.util.LatLng;

public class ReportSummary {
    LatLng latLng;
    Long count;

    public ReportSummary(LatLng latLng, Long count) {
        this.latLng = latLng;
        this.count = count;
    }
}
