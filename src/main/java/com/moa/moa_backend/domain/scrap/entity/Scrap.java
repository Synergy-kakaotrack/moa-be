package com.moa.moa_backend.domain.scrap.entity;

import com.moa.moa_backend.domain.draft.entity.RecMethod;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;

@Getter
@Entity
@Table(name = "scraps")
public class Scrap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "scrap_id")
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // === 원본 데이터 ===
    @Column(name = "raw_html", nullable = false, columnDefinition = "TEXT")
    private String rawHtml;

    // === 메타데이터 ===
    @Column(name = "subtitle", nullable = false, length = 120)
    private String subtitle;

    @Column(name = "stage", nullable = false, length = 30)
    private String stage;

    @Column(name = "memo", columnDefinition = "TEXT")
    private String memo;

    // === AI 출처 정보 ===
    @Column(name = "ai_source", nullable = false, length = 30)
    private String aiSource;

    @Column(name = "ai_source_url", nullable = false, columnDefinition = "TEXT")
    private String aiSourceUrl;

    // === 추천 수용 여부 ===
    @Column(name = "user_rec_project", nullable = false)
    private boolean userRecProject;

    @Column(name = "user_rec_stage", nullable = false)
    private boolean userRecStage;

    @Column(name = "user_rec_subtitle", nullable = false)
    private boolean userRecSubtitle;

    // === 타임스탬프 ===
    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "rec_method", nullable = false, length = 20)
    private RecMethod recMethod;

    protected Scrap() {}

    public static Scrap create(
            Long projectId,
            Long userId,
            String rawHtml,
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
        s.rawHtml = rawHtml;
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
