package com.moa.moa_backend.domain.digest.scheduler;

import com.moa.moa_backend.domain.digest.service.StageDigestService;
import com.moa.moa_backend.domain.scrap.repository.ScrapDigestQueryRepository;
import com.moa.moa_backend.domain.scrap.repository.projection.DigestRefreshTarget;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class StageDigestAutoRefreshSchedulerTest {

    @Autowired
    StageDigestAutoRefreshScheduler scheduler;

    @MockitoBean
    ScrapDigestQueryRepository scrapDigestQueryRepository;

    @MockitoBean
    StageDigestService stageDigestService;

    @Test
    void runDailyRefresh_calls_refresh_for_each_target() {
        // given
        List<DigestRefreshTarget> targets = List.of(
                new DigestRefreshTarget(1L, 10L, "설계"),
                new DigestRefreshTarget(1L, 10L, "구현")
        );

        when(scrapDigestQueryRepository.findRecentTargetsForAutoRefresh(any(), anyInt()))
                .thenReturn(targets);

        // when
        scheduler.runDailyRefresh();

        // then
        verify(stageDigestService).refresh(1L, 10L, "설계");
        verify(stageDigestService).refresh(1L, 10L, "구현");
    }
}
