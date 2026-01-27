package com.moa.moa_backend.domain.digest.scheduler;

import com.moa.moa_backend.domain.digest.service.StageDigestService;
import com.moa.moa_backend.domain.scrap.repository.projection.DigestRefreshTarget;
import com.moa.moa_backend.domain.scrap.repository.ScrapDigestQueryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class StageDigestAutoRefreshScheduler {

    private final ScrapDigestQueryRepository scrapDigestQueryRepository;
    private final StageDigestService stageDigestService;

    /**
     * 스케줄러 중복 실행 방지 (단일 인스턴스 한정)
     */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 하루에 최대 몇 개의 (userId, projectId, stage)를 처리할지
     */
    @Value("${moa.digest.auto-refresh.daily-limit:200}")
    private int dailyLimit;

    /**
     * 최근 7일 내 활동한 stage만 대상
     */
    @Value("${moa.digest.auto-refresh.lookback-days:7}")
    private int lookbackDays;

    public StageDigestAutoRefreshScheduler(
            ScrapDigestQueryRepository scrapDigestQueryRepository,
            StageDigestService stageDigestService
    ) {
        this.scrapDigestQueryRepository = scrapDigestQueryRepository;
        this.stageDigestService = stageDigestService;
    }

    /**
     * 매일 1회 자동 refresh
     * - KST 기준 새벽 4시
     */
    @Scheduled(cron = "${moa.digest.auto-refresh.cron:0 0 4 * * *}", zone = "Asia/Seoul")
    public void runDailyRefresh() {

        // 중복 실행 방지
        if (!running.compareAndSet(false, true)) {
            log.info("[DIGEST][SCHED] already running. skip.");
            return;
        }

        Instant start = Instant.now();
        int success = 0;
        int fail = 0;

        try {
            Instant since = Instant.now().minus(Duration.ofDays(lookbackDays));

            // 최근 활동 기준으로 대상 stage 목록 뽑기 (limit 적용)
            List<DigestRefreshTarget> targets =
                    scrapDigestQueryRepository.findRecentTargetsForAutoRefresh(since, dailyLimit);

            log.info("[DIGEST][SCHED] start. targets={}, limit={}, lookbackDays={}",
                    targets.size(), dailyLimit, lookbackDays);

            // 순차 실행 + 한 건 실패해도 다음 건 진행
            for (DigestRefreshTarget t : targets) {
                try {
                    stageDigestService.refresh(t.userId(), t.projectId(), t.stage());
                    success++;
                } catch (Exception e) {
                    fail++;
                    log.warn("[DIGEST][SCHED] refresh failed. userId={}, projectId={}, stage={}",
                            t.userId(), t.projectId(), t.stage(), e);
                }
            }

        } finally {
            running.set(false);
            long ms = Duration.between(start, Instant.now()).toMillis();
            log.info("[DIGEST][SCHED] done. success={}, fail={}, elapsedMs={}", success, fail, ms);
        }
    }
}
