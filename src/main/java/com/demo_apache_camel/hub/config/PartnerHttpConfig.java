package com.demo_apache_camel.hub.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

/**
 * [PARTNER HTTP CONFIGURATION]
 * Cấu hình RestTemplate chuyên dụng cho việc gọi OAuth2 token endpoint của đối tác.
 * - Connect timeout & Read timeout riêng, lấy từ PartnerOAuth2Properties.
 * - @EnableScheduling: Bật tính năng @Scheduled cho PartnerTokenRefreshScheduler.
 */
@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class PartnerHttpConfig {

    private final PartnerOAuth2Properties oAuth2Properties;

    /**
     * RestTemplate dành riêng cho OAuth2 token requests (không dùng chung với các HTTP call khác).
     * - Named bean "partnerOAuth2RestTemplate" để inject chính xác vào PartnerTokenManager.
     */
    @Bean("partnerOAuth2RestTemplate")
    public RestTemplate partnerOAuth2RestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(oAuth2Properties.getConnectTimeoutMs());
        factory.setReadTimeout(oAuth2Properties.getReadTimeoutMs());

        log.info("[PARTNER-HTTP] Khởi tạo OAuth2 RestTemplate: connectTimeout={}ms, readTimeout={}ms",
                oAuth2Properties.getConnectTimeoutMs(), oAuth2Properties.getReadTimeoutMs());

        return new RestTemplate(factory);
    }
}
