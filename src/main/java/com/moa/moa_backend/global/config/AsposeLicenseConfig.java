package com.moa.moa_backend.global.config;

import com.aspose.html.License;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;

@Configuration
public class AsposeLicenseConfig {

    @PostConstruct
    public void applyAsposeLicense() {
        try (InputStream is = new ClassPathResource("Aspose.Total.lic").getInputStream()) {
            License license = new License();
            license.setLicense(is);
        } catch (Exception e) {
            // 운영에서는 로그로 남기고 실패 처리(또는 기동 실패) 정책을 선택하세요.
        }
    }
}
