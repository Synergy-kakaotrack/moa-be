package com.moa.moa_backend.domain.digest.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(
        name = "project_digests",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_project_digests_one_per_project",
                        columnNames = {"user_id", "project_id"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectDigest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "project_digest_id")
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "digest_kind", nullable = false)
    private DigestKind digestKind;

    /**
     * CUSTOM 요약일 때만 값이 존재
     */
    @Column(name = "prompt_text")
    private String promptText;

    /**
     * 요약 결과 (markdown)
     */
    @Column(name = "digest_text")
    private String digestText;

    /**
     * 이 요약이 반영한 스크랩들의 최신 시각
     */
    @Column(name = "source_last_updated_at")
    private Instant sourceLastUpdatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /* =========================
       Factory methods
       ========================= */

    /**
     * 최초 생성용 (아직 요약이 없는 상태)
     * - upsert 패턴에서 기본 엔트리로 사용
     */
    public static ProjectDigest createEmpty(Long userId, Long projectId) {
        ProjectDigest digest = new ProjectDigest();
        digest.userId = userId;
        digest.projectId = projectId;
        digest.digestKind = DigestKind.DEFAULT;
        digest.promptText = null;
        digest.digestText = null;
        digest.sourceLastUpdatedAt = null;
        return digest;
    }

    public static ProjectDigest createDefault(
            Long userId,
            Long projectId,
            String digestText,
            Instant sourceLastUpdatedAt
    ) {
        ProjectDigest digest = new ProjectDigest();
        digest.userId = userId;
        digest.projectId = projectId;
        digest.digestKind = DigestKind.DEFAULT;
        digest.promptText = null;
        digest.digestText = digestText;
        digest.sourceLastUpdatedAt = sourceLastUpdatedAt;
        return digest;
    }

    public static ProjectDigest createCustom(
            Long userId,
            Long projectId,
            String promptText,
            String digestText,
            Instant sourceLastUpdatedAt
    ) {
        ProjectDigest digest = new ProjectDigest();
        digest.userId = userId;
        digest.projectId = projectId;
        digest.digestKind = DigestKind.CUSTOM;
        digest.promptText = promptText;
        digest.digestText = digestText;
        digest.sourceLastUpdatedAt = sourceLastUpdatedAt;
        return digest;
    }

    /* =========================
       Update
       ========================= */

    /**
     * DEFAULT / CUSTOM 공통 갱신
     */
    public void update(
            DigestKind digestKind,
            String promptText,
            String digestText,
            Instant sourceLastUpdatedAt
    ) {
        this.digestKind = digestKind;
        this.promptText = promptText;
        this.digestText = digestText;
        this.sourceLastUpdatedAt = sourceLastUpdatedAt;
    }

    /* =========================
       JPA Lifecycle
       ========================= */

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    private void onUpdate() {
        this.updatedAt = Instant.now();
    }
}

