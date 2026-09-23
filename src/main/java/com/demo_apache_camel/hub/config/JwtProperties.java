package com.demo_apache_camel.hub.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * [JWT SECURITY PROPERTIES - PRODUCTION GRADE]
 * Cấu hình các thông số bảo mật JWT của Hub:
 * - secret: Khóa ký HMAC-SHA256 (base64-encoded, tối thiểu 256-bit)
 * - expirationSeconds: Hạn dùng của token Client
 * - issuer: Định danh Hub phát hành token
 * - clockSkewSeconds: Khoảng lệch đồng hồ cho phép giữa các service
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "hub.security.jwt")
public class JwtProperties {

    /**
     * Khóa ký HMAC-SHA256 (min 256-bit / 32 chars).
     * Production: đọc từ biến môi trường JWT_SECRET hoặc Vault.
     */
    private String secret = "CHANGE_ME_production_256bit_secret_key_hub_2026";

    /**
     * Thời gian sống của JWT (giây). Mặc định 3600 = 1 giờ.
     */
    private long expirationSeconds = 3600L;

    /**
     * Issuer (iss claim) phải khớp trong JWT nhận được.
     */
    private String issuer = "enterprise-service-hub";

    /**
     * Khoảng lệch đồng hồ cho phép (giây). Tránh reject token hợp lệ do đồng hồ server lệch nhau.
     * Giá trị an toàn: 30-60 giây.
     */
    private int clockSkewSeconds = 60;
}
