package com.smartcity.backend.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "report_h3",
        indexes = {
                @Index(name = "idx_h3_token", columnList = "h3token")
        }
)

@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportH3 {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @Setter
    @Getter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rep_id", referencedColumnName = "reportId", nullable = false)
    private Report report;
    @Setter
    @Getter
    @Column(name = "h3token" ,  nullable = false)
    private Long h3Token;

}