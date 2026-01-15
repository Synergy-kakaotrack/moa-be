package com.moa.moa_backend.domain.scrap.entity;

import com.moa.moa_backend.domain.draft.entity.RecMethod;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;

@Getter
@Entity
@Table(
        name = "scraps",
        indexes = {
                @Index(name = "idx_scraps_user_id", columnList = "user_id"),
                @Index(name = "idx_scraps_captured_at", columnList = "captured_at")
        }
)
public class Scrap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "scrap_id")
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "raw_html_gzip", nullable = false, columnDefinition = "bytea")
    private byte[] rawHtmlGzip;

    @Column(name = "subtitle", nullable = false, length = 120)
    private String subtitle;

    @Column(name = "stage", nullable = false, length = 30)
    private String stage;

    @Column(name = "memo", columnDefinition = "TEXT")
    private String memo;

    @Column(name = "ai_source", nullable = false, length = 30)
    private String aiSource;

    @Column(name = "ai_source_url", nullable = false, columnDefinition = "TEXT")
    private String aiSourceUrl;

    @Column(name = "user_rec_project", nullable = false)
    private boolean userRecProject;

    @Column(name = "user_rec_stage", nullable = false)
    private boolean userRecStage;

    @Column(name = "user_rec_subtitle", nullable = false)
    private boolean userRecSubtitle;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "rec_method", nullable = false, length = 20)
    private RecMethod recMethod;

    protected Scrap() {}

    public static Scrap create(
            Long projectId,
            Long userId,
            byte[] rawHtmlGzip,
            String subtitle,
            String stage,
            String memo,
            String aiSource,
            String aiSourceUrl,
            boolean userRecProject,
            boolean userRecStage,
            boolean userRecSubtitle,
            Instant capturedAt,
            RecMethod recMethod
    ) {
        Scrap s = new Scrap();
        s.projectId = projectId;
        s.userId = userId;
        s.rawHtmlGzip = rawHtmlGzip;
        s.subtitle = subtitle;
        s.stage = stage;
        s.memo = memo;
        s.aiSource = aiSource;
        s.aiSourceUrl = aiSourceUrl;
        s.userRecProject = userRecProject;
        s.userRecStage = userRecStage;
        s.userRecSubtitle = userRecSubtitle;
        s.capturedAt = capturedAt;
        s.recMethod = recMethod;
        return s;
    }
}
