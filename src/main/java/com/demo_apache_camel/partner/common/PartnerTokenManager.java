package com.demo_apache_camel.partner.common;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [PARTNER TOKEN LIFECYCLE MANAGER]
 * Quản lý vòng đời OAuth2 Token của các đối tác bên ngoài (Vietjet, Viettel, VNPAY...):
 * 1. Thread-safe in-memory cache với hạn sử dụng (TTL).
 * 2. Tự động kiểm tra và làm mới token khi hết hạn hoặc bị thu hồi (401 Retry).
 */
@Slf4j
@Service
public class PartnerTokenManager {

    private final Map<String, CachedToken> tokenStore = new ConcurrentHashMap<>();

    @Getter
    @AllArgsConstructor
    public static class CachedToken {
        private final String token;
        private final Instant expiresAt;

        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    public String getOrRefreshToken(String partnerCode) {
        CachedToken cached = tokenStore.get(partnerCode);

        if (cached == null || cached.isExpired()) {
            synchronized (this) {
                cached = tokenStore.get(partnerCode);
                if (cached == null || cached.isExpired()) {
                    log.info("[PARTNER-TOKEN] Token của đối tác '{}' đã hết hạn hoặc chưa có. Đang cấp mới...", partnerCode);
                    cached = requestNewTokenFromPartner(partnerCode);
                    tokenStore.put(partnerCode, cached);
                    log.info("[PARTNER-TOKEN] Đã cấp token mới thành công cho '{}' (Hạn dùng 3600s)", partnerCode);
                }
            }
        }
        return cached.getToken();
    }

    public void evictToken(String partnerCode) {
        log.warn("[PARTNER-TOKEN] Thu hồi token đối tác '{}' do nhận phản hồi 401 từ hệ thống ngoài", partnerCode);
        tokenStore.remove(partnerCode);
    }

    private CachedToken requestNewTokenFromPartner(String partnerCode) {
        String generatedToken = "OAUTH2-" + partnerCode + "-" + UUID.randomUUID().toString().substring(0, 16).toUpperCase();
        // Giả lập hạn dùng 3600 giây (1 giờ)
        Instant expiresAt = Instant.now().plusSeconds(3600);
        return new CachedToken(generatedToken, expiresAt);
    }
}
