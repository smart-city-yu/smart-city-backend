package com.smartcity.backend.service;


import com.smartcity.backend.model.Report;
import com.smartcity.backend.model.ReportH3;
import com.smartcity.backend.repository.ReportH3Repository;
import com.uber.h3core.H3Core;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor

public class H3ReportService {
    private final ReportH3Repository reportH3Repository;
    private final H3CoreService h3Core;

    public List<Report> getAllReports(Long h3Token) {
        return reportH3Repository.findReportsByH3Token(h3Token);
    }

    public Long CountReports(Long h3Token) {
        return reportH3Repository.countByH3Token(h3Token);
    }
    public Map<Long,Long> countReports(List<Long> tokens) {
        List<Object[]> result = reportH3Repository.countByH3Tokens(tokens);
        return result.stream().collect(Collectors.toMap(r-> (Long) r[0] , r-> (Long) r[1]));
    }
    public void InsertReportH3(Report report ) {
        for (int i = 1 ; i<=8; i++) {
            Long inx = h3Core.getCell(report.getLat() , report.getLon() , i);
             reportH3Repository.save(ReportH3.builder()
                    .h3Token(inx)
                    .report(report)
                    .build());
        }
    }





}
