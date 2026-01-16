package com.moa.moa_backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

//Spring이 실제로 부팅되면서 Flyway까지 도는지를 CI에서 강제로 검증하는 스모크 테스트
@SpringBootTest
class ContextLoadsTest {
    @Test
    void contextLoads() {}
}

