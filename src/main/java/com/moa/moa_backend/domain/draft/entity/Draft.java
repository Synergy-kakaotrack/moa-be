package com.moa.moa_backend.domain.draft.entity;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;

@Getter
@Entity
@Table(
        name = "drafts",
        indexes = {
                @Index(name = "idx_drafts_user_id", columnList = "user_id"),
                @Index(name = "idx_drafts_expired_at", columnList = "draft_expired_at")
        }
)
public class Draft {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "draft_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "rec_project_id")
    private Long recProjectId;

    @Column(name = "content_plain", nullable = false, columnDefinition = "TEXT")
    private String contentPlain;

    @Column(name = "rec_stage", nullable = false, length = 30)
    private String recStage;

    @Column(name = "rec_subtitle", length = 120)
    private String recSubtitle;

    @Column(name = "ai_source", nullable = false, length = 30)
    private String aiSource;

    @Column(name = "ai_source_url", nullable = false, columnDefinition = "TEXT")
    private String aiSourceUrl;

    @Column(name = "draft_created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "draft_expired_at", nullable = false)
    private Instant expiredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "rec_method", nullable = false, length = 20)
    private RecMethod recMethod;

    protected Draft() {}

    // 생성용 팩토리(서비스에서 사용)
    public static Draft create(
            Long userId,
            String contentPlain,
            String aiSource,
            String aiSourceUrl,
            Long recProjectId,
            String recStage,
            String recSubtitle,
            RecMethod recMethod,
            Instant now,
            Instant expiredAt
    ) {
        Draft d = new Draft();
        d.userId = userId;
        d.contentPlain = contentPlain;
        d.aiSource = aiSource;
        d.aiSourceUrl = aiSourceUrl;
        d.recProjectId = recProjectId;
        d.recStage = recStage;
        d.recSubtitle = recSubtitle;
        d.recMethod = recMethod;
        d.createdAt = now;
        d.expiredAt = expiredAt;
        return d;
    }

    public boolean isExpired(Instant now) {
        return expiredAt != null && expiredAt.isBefore(now);
    }


}
