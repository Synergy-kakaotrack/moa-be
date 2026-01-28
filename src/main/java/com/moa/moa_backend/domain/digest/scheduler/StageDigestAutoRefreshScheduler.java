package com.moa.moa_backend.domain.digest.scheduler;

import com.moa.moa_backend.domain.digest.dto.StageDigestResponse;
import com.moa.moa_backend.domain.digest.service.StageDigestService;
import com.moa.moa_backend.domain.scrap.repository.ScrapDigestQueryRepository;
import com.moa.moa_backend.domain.scrap.repository.projection.DigestRefreshTarget;
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
     * 최근 N일 내 활동한 stage만 대상
     */
    @Value("${moa.digest.auto-refresh.lookback-days:7}")
    private int lookbackDays;

    /**
     * 기본 요청 페이싱(ms)
     * - 모든 target 사이에 고정으로 쉬는 시간
     */
    @Value("${moa.digest.auto-refresh.delay-ms:200}")
    private long baseDelayMs;

    /**
     * RATE_LIMITED(429) 발생 시 backoff 시작값(ms)
     */
    @Value("${moa.digest.auto-refresh.backoff.initial-ms:1000}")
    private long backoffInitialMs;

    /**
     * RATE_LIMITED(429) backoff 최대값(ms)
     */
    @Value("${moa.digest.auto-refresh.backoff.max-ms:15000}")
    private long backoffMaxMs;

    /**
     * RATE_LIMITED 연속 발생 시 backoff 배수
     * - 예: 1.8 → 1000, 1800, 3240, 5832...
     */
    @Value("${moa.digest.auto-refresh.backoff.multiplier:1.8}")
    private double backoffMultiplier;

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
        int skipped = 0;
        int unknown = 0;

        //rate limit 완화용 동적 backoff
        long currentBackoffMs = 0;
        int consecutiveRateLimited = 0;

        try {
            Instant since = Instant.now().minus(Duration.ofDays(lookbackDays));

            List<DigestRefreshTarget> targets =
                    scrapDigestQueryRepository.findRecentTargetsForAutoRefresh(since, dailyLimit);

            log.info("[DIGEST][SCHED] start. targets={}, limit={}, lookbackDays={}, baseDelayMs={}",
                    targets.size(), dailyLimit, lookbackDays, baseDelayMs);

            for (DigestRefreshTarget t : targets) {
                try {
                    StageDigestResponse res = stageDigestService.refresh(t.userId(), t.projectId(), t.stage());

                    StageDigestResponse.Refresh refresh = (res.meta() != null) ? res.meta().refresh() : null;
                    String status = (refresh != null) ? refresh.status() : null;

                    if ("SUCCESS".equals(status)) {
                        success++;
                        // 성공이면 rate-limit backoff 리셋(또는 완화)
                        consecutiveRateLimited = 0;
                        currentBackoffMs = 0;

                    } else if ("SKIPPED".equals(status)) {
                        skipped++;
                        // 스킵도 시스템 부하를 올리는 상황은 아니므로 완화
                        consecutiveRateLimited = 0;
                        currentBackoffMs = 0;

                    } else if ("FAILED".equals(status)) {
                        fail++;

                        String errorCode = refresh != null ? refresh.errorCode() : null;
                        String message = refresh != null ? refresh.message() : null;

                        log.warn("[DIGEST][SCHED] refresh FAILED. userId={}, projectId={}, stage={}, errorCode={}, msg={}",
                                t.userId(), t.projectId(), t.stage(), errorCode, message);

                        // RATE_LIMITED면 점진적 backoff 증가
                        if ("RATE_LIMITED".equals(errorCode)) {
                            consecutiveRateLimited++;

                            // 1) 응답이 retryAfterSeconds를 주면 우선 사용
                            Integer retryAfterSeconds = refresh.retryAfterSeconds();
                            if (retryAfterSeconds != null && retryAfterSeconds > 0) {
                                currentBackoffMs = Math.min(backoffMaxMs, retryAfterSeconds.longValue() * 1000L);
                            } else {
                                // 2) 없으면 지수 백오프
                                if (currentBackoffMs <= 0) currentBackoffMs = backoffInitialMs;
                                else currentBackoffMs = Math.min(backoffMaxMs, (long) Math.ceil(currentBackoffMs * backoffMultiplier));
                            }

                            log.warn("[DIGEST][SCHED] rate limited. consecutive={}, nextBackoffMs={}",
                                    consecutiveRateLimited, currentBackoffMs);
                        } else {
                            // RATE_LIMITED 외 실패는 backoff 리셋 (원하면 유지해도 됨)
                            consecutiveRateLimited = 0;
                            currentBackoffMs = 0;
                        }

                    } else {
                        unknown++;
                        log.warn("[DIGEST][SCHED] refresh status unknown. userId={}, projectId={}, stage={}",
                                t.userId(), t.projectId(), t.stage());
                    }

                } catch (Exception e) {
                    // refresh 내부에서 409(inFlight) 같은 건 예외로 던질 수 있으니 여기는 방어
                    fail++;
                    log.warn("[DIGEST][SCHED] refresh threw exception. userId={}, projectId={}, stage={}",
                            t.userId(), t.projectId(), t.stage(), e);

                    // 예외도 일단 backoff 리셋(또는 유지). 여기선 리셋으로 둠.
                    consecutiveRateLimited = 0;
                    currentBackoffMs = 0;
                }

                //페이싱: baseDelay + (필요 시) backoff
                long sleepMs = baseDelayMs + currentBackoffMs;
                if (sleepMs > 0) {
                    try {
                        Thread.sleep(sleepMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.warn("[DIGEST][SCHED] interrupted. stop.");
                        break;
                    }
                }
            }

        } finally {
            running.set(false);
            long ms = Duration.between(start, Instant.now()).toMillis();
            log.info("[DIGEST][SCHED] done. success={}, fail={}, skipped={}, unknown={}, elapsedMs={}",
                    success, fail, skipped, unknown, ms);
        }
    }
}
