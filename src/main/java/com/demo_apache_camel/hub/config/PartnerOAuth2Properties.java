package com.demo_apache_camel.hub.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * [PARTNER OAUTH2 PROPERTIES - PRODUCTION GRADE]
 * Cấu hình OAuth2 Client Credentials Flow cho từng đối tác bên ngoài.
 *
 * Mỗi đối tác (VIETJET, VIETTEL, BAOVIET...) có endpoint, clientId, secret riêng biệt.
 * Toàn bộ sensitive values phải đọc từ biến môi trường (không commit secret vào git).
 *
 * Chiến lược Graceful Fallback:
 * - Nếu tokenUrl chứa "localhost" hoặc không kết nối được → dùng mock token (dev/test).
 * - Production: set biến môi trường VIETJET_TOKEN_URL, VIETJET_CLIENT_ID, VIETJET_CLIENT_SECRET.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "hub.partner-oauth2")
public class PartnerOAuth2Properties {

    /**
     * Số giây còn lại trước khi token hết hạn để trigger proactive refresh.
     * Mặc định 300s = 5 phút. Tránh race condition khi token hết hạn giữa chừng request.
     */
    private int tokenRefreshBufferSeconds = 300;

    /**
     * Timeout kết nối tới Partner OAuth2 server (ms).
     */
    private int connectTimeoutMs = 3000;

    /**
     * Timeout đọc response từ Partner OAuth2 server (ms).
     */
    private int readTimeoutMs = 5000;

    /**
     * Số lần retry khi OAuth2 token request thất bại do network.
     */
    private int maxRetryAttempts = 3;

    /**
     * Delay giữa các lần retry (ms).
     */
    private long retryDelayMs = 1000L;

    /**
     * Map cấu hình từng đối tác: key = partnerCode (VIETJET, VIETTEL, BAOVIET...)
     */
    private Map<String, PartnerCredentials> partners = new HashMap<>();

    @Data
    public static class PartnerCredentials {
        /** URL endpoint POST để lấy Access Token (OAuth2 token endpoint) */
        private String tokenUrl;

        /** Client ID đã đăng ký với đối tác */
        private String clientId;

        /** Client Secret (đọc từ biến môi trường trong production) */
        private String clientSecret;

        /** OAuth2 scope yêu cầu */
        private String scope;

        /**
         * Kiểm tra có phải môi trường local/test không (để dùng mock fallback).
         * Trả về true nếu tokenUrl chứa "localhost", "mock", "test", hoặc null/rỗng.
         */
        public boolean isMockEnvironment() {
            return tokenUrl == null
                    || tokenUrl.isBlank()
                    || tokenUrl.contains("localhost")
                    || tokenUrl.contains("mock")
                    || tokenUrl.contains("test");
        }
    }
}
