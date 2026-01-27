package com.moa.moa_backend.domain.digest.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Getter
@NoArgsConstructor
@Entity
@Table(
        name = "stage_digests",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_stage_digests",
                columnNames = {"user_id", "project_id", "stage"}
        )
)
public class StageDigest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stage_digest_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "stage", nullable = false, length = 30)
    private String stage;

    @Column(name = "digest_text")
    private String digestText;

    @Column(name = "source_last_captured_at")
    private OffsetDateTime sourceLastCapturedAt;    //이 요약이 만들어질 당시 기준이 된 Scrap의 최신 capturedAt

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static StageDigest create(Long userId, Long projectId, String stage, String digestText, OffsetDateTime sourceLastCapturedAt) {
        StageDigest d = new StageDigest();
        d.userId = userId;
        d.projectId = projectId;
        d.stage = stage;
        d.digestText = digestText;
        d.sourceLastCapturedAt = sourceLastCapturedAt;
        return d;
    }

    public void updateDigest(String digestText, OffsetDateTime sourceLastCapturedAt) {
        this.digestText = digestText;
        this.sourceLastCapturedAt = sourceLastCapturedAt;
    }


    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
