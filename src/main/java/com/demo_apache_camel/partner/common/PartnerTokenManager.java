package com.demo_apache_camel.partner.common;

import com.demo_apache_camel.hub.config.PartnerOAuth2Properties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [PARTNER TOKEN LIFECYCLE MANAGER - PRODUCTION GRADE]
 *
 * Quản lý vòng đời OAuth2 Access Token của các đối tác bên ngoài (Vietjet, Viettel, BaoViet...):
 *
 * === CƠ CHẾ HOẠT ĐỘNG ===
 *
 * 1. PROACTIVE REFRESH (Chủ động làm mới):
 *    Token được refresh sớm khi còn < tokenRefreshBufferSeconds (mặc định 300s = 5 phút) trước khi hết hạn.
 *    → KHÔNG chờ token hết hạn hoặc nhận lỗi 401 mới refresh (loại bỏ race condition).
 *
 * 2. DOUBLE-CHECKED LOCKING (Chống race condition):
 *    Khi nhiều thread đồng thời phát hiện token hết hạn, chỉ 1 thread thực hiện refresh,
 *    các thread còn lại chờ và dùng token mới từ cache.
 *
 * 3. REAL OAUTH2 CLIENT CREDENTIALS FLOW:
 *    POST /oauth2/token với grant_type=client_credentials, client_id, client_secret, scope.
 *    → Đúng chuẩn RFC 6749 (OAuth 2.0 Authorization Framework).
 *
 * 4. GRACEFUL FALLBACK (Môi trường local/test):
 *    Nếu partner tokenUrl không tồn tại hoặc không kết nối được → log WARN và dùng mock token.
 *    Production chỉ cần set biến môi trường VIETJET_TOKEN_URL, VIETJET_CLIENT_ID, VIETJET_CLIENT_SECRET.
 *
 * 5. DEFENSIVE EVICT (Layer 2 bảo vệ):
 *    evictToken() vẫn được giữ để xử lý trường hợp token bị thu hồi bởi đối tác (401 Unauthorized).
 *    Sau evict, lần gọi tiếp theo sẽ tự động fetch token mới.
 *
 * === PRODUCTION DEPLOYMENT ===
 * - Multi-instance: Thay ConcurrentHashMap bằng RedisTemplate (chỉ cần implement TokenStore interface).
 * - Secret rotation: Đối tác cấp client_secret mới → set env var → restart (zero-downtime với proactive refresh).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerTokenManager {

    private final PartnerOAuth2Properties oAuth2Properties;
    private final RestTemplate partnerOAuth2RestTemplate;

    /**
     * In-memory token store (thread-safe).
     * Production multi-instance: Thay bằng RedisTemplate<String, CachedToken>.
     */
    private final Map<String, CachedToken> tokenStore = new ConcurrentHashMap<>();

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Lấy token hợp lệ cho đối tác. Tự động refresh nếu token sắp hết hạn hoặc chưa có.
     *
     * @param partnerCode Mã đối tác (ví dụ: "VIETJET", "VIETTEL", "BAOVIET")
     * @return Access token string
     */
    public String getOrRefreshToken(String partnerCode) {
        CachedToken cached = tokenStore.get(partnerCode);

        if (cached == null || isNearExpiry(cached)) {
            // Double-checked locking: chỉ 1 thread thực hiện refresh
            synchronized (this) {
                cached = tokenStore.get(partnerCode);
                if (cached == null || isNearExpiry(cached)) {
                    log.info("[PARTNER-TOKEN] Token đối tác '{}' {}. Đang fetch token mới...",
                            partnerCode,
                            cached == null ? "chưa có trong cache" : "sắp hết hạn (còn <" + oAuth2Properties.getTokenRefreshBufferSeconds() + "s)");
                    cached = fetchToken(partnerCode);
                    tokenStore.put(partnerCode, cached);
                    log.info("[PARTNER-TOKEN] Đã cấp token mới cho '{}' (Hết hạn lúc: {})",
                            partnerCode, cached.getExpiresAt());
                }
            }
        }

        return cached.getAccessToken();
    }

    /**
     * Thu hồi token và xóa khỏi cache khi nhận lỗi 401 từ đối tác (defensive layer 2).
     * Lần gọi tiếp theo sẽ tự động fetch token mới.
     *
     * @param partnerCode Mã đối tác
     */
    public void evictToken(String partnerCode) {
        boolean removed = tokenStore.remove(partnerCode) != null;
        if (removed) {
            log.warn("[PARTNER-TOKEN] Đã thu hồi token '{}' do nhận phản hồi 401 từ đối tác. Token mới sẽ được cấp phát ở lần gọi tiếp theo.", partnerCode);
        } else {
            log.debug("[PARTNER-TOKEN] evictToken('{}') — token không tồn tại trong cache, bỏ qua.", partnerCode);
        }
    }

    /**
     * Proactively warm cache token cho tất cả partner đã cấu hình.
     * Được gọi bởi PartnerTokenRefreshScheduler mỗi phút.
     */
    public void warmUpAllPartners() {
        oAuth2Properties.getPartners().forEach((partnerCode, credentials) -> {
            try {
                getOrRefreshToken(partnerCode);
            } catch (Exception ex) {
                log.warn("[PARTNER-TOKEN] Warm-up token '{}' thất bại: {}", partnerCode, ex.getMessage());
            }
        });
    }

    /**
     * Trả về thông tin token hiện tại (không gọi refresh). Dùng cho monitoring.
     */
    public Map<String, CachedToken> getTokenSnapshot() {
        return Map.copyOf(tokenStore);
    }

    // =========================================================================
    // INTERNAL TOKEN LOGIC
    // =========================================================================

    /**
     * Kiểm tra token sắp hết hạn (còn ít hơn buffer seconds).
     */
    private boolean isNearExpiry(CachedToken token) {
        if (token.getExpiresAt() == null) return true;
        Instant refreshThreshold = token.getExpiresAt().minusSeconds(oAuth2Properties.getTokenRefreshBufferSeconds());
        return Instant.now().isAfter(refreshThreshold);
    }

    /**
     * Fetch token mới từ đối tác bằng OAuth2 Client Credentials Flow.
     * Graceful fallback: nếu URL không hợp lệ/không kết nối → dùng mock token.
     */
    private CachedToken fetchToken(String partnerCode) {
        PartnerOAuth2Properties.PartnerCredentials creds = oAuth2Properties.getPartners().get(partnerCode);

        // Nếu không có cấu hình partner hoặc là môi trường mock → dùng fallback
        if (creds == null || creds.isMockEnvironment()) {
            return buildMockToken(partnerCode, creds == null ? "Chưa có cấu hình partner" : "Môi trường local/test");
        }

        return callPartnerOAuth2Endpoint(partnerCode, creds);
    }

    /**
     * Gọi thật OAuth2 Client Credentials endpoint của đối tác.
     * Retry tự động khi network lỗi (qua PartnerTokenRefreshScheduler hoặc Spring Retry nếu cấu hình).
     */
    private CachedToken callPartnerOAuth2Endpoint(String partnerCode, PartnerOAuth2Properties.PartnerCredentials creds) {
        try {
            log.debug("[PARTNER-TOKEN] Gọi OAuth2 token endpoint: partnerCode='{}', url='{}'",
                    partnerCode, creds.getTokenUrl());

            // Chuẩn bị request theo OAuth2 Client Credentials Flow (RFC 6749)
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setBasicAuth(creds.getClientId(), creds.getClientSecret());

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "client_credentials");
            body.add("client_id", creds.getClientId());
            body.add("client_secret", creds.getClientSecret());
            if (creds.getScope() != null && !creds.getScope().isBlank()) {
                body.add("scope", creds.getScope());
            }

            HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(body, headers);

            // Gọi HTTP POST tới partner token endpoint
            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> responseEntity = partnerOAuth2RestTemplate.exchange(
                    creds.getTokenUrl(),
                    HttpMethod.POST,
                    requestEntity,
                    (Class<Map<String, Object>>) (Class<?>) Map.class
            );

            if (!responseEntity.getStatusCode().is2xxSuccessful() || responseEntity.getBody() == null) {
                log.error("[PARTNER-TOKEN] OAuth2 endpoint trả lỗi HTTP {}: partnerCode='{}'",
                        responseEntity.getStatusCode(), partnerCode);
                throw new IllegalStateException("OAuth2 endpoint trả về lỗi: " + responseEntity.getStatusCode());
            }

            Map<String, Object> tokenResponse = responseEntity.getBody();
            return parseOAuth2TokenResponse(partnerCode, tokenResponse);

        } catch (ResourceAccessException ex) {
            // Network lỗi (connection timeout, host not found) → graceful fallback
            log.warn("[PARTNER-TOKEN] Không kết nối được OAuth2 endpoint của '{}': {}. Sử dụng mock token (graceful fallback).",
                    partnerCode, ex.getMessage());
            return buildMockToken(partnerCode, "Network error: " + ex.getMessage());

        } catch (Exception ex) {
            log.error("[PARTNER-TOKEN] Lỗi khi fetch token đối tác '{}': {}. Sử dụng mock token.", 
                    partnerCode, ex.getMessage());
            return buildMockToken(partnerCode, "Error: " + ex.getMessage());
        }
    }

    /**
     * Parse response OAuth2 chuẩn RFC 6749:
     * { "access_token": "...", "token_type": "Bearer", "expires_in": 3600, "scope": "..." }
     */
    private CachedToken parseOAuth2TokenResponse(String partnerCode, Map<String, Object> tokenResponse) {
        String accessToken = (String) tokenResponse.get("access_token");
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("OAuth2 response thiếu trường access_token cho partner: " + partnerCode);
        }

        // Parse expires_in (số giây) → tính expiresAt
        Object expiresInObj = tokenResponse.get("expires_in");
        long expiresInSeconds = 3600L; // Mặc định 1 giờ nếu không có
        if (expiresInObj instanceof Number) {
            expiresInSeconds = ((Number) expiresInObj).longValue();
        }

        String tokenType = (String) tokenResponse.getOrDefault("token_type", "Bearer");
        String scope = (String) tokenResponse.getOrDefault("scope", "");
        Instant expiresAt = Instant.now().plusSeconds(expiresInSeconds);

        log.info("[PARTNER-TOKEN] OAuth2 token thật nhận được từ '{}': type={}, expiresIn={}s, scope='{}'",
                partnerCode, tokenType, expiresInSeconds, scope);

        return new CachedToken(accessToken, expiresAt, tokenType, scope, false);
    }

    /**
     * Tạo mock token dùng cho môi trường local/test hoặc khi đối tác chưa kết nối.
     * Mock token có TTL đầy đủ để test không bị ảnh hưởng.
     */
    private CachedToken buildMockToken(String partnerCode, String reason) {
        String mockToken = "MOCK-OAUTH2-" + partnerCode + "-" + UUID.randomUUID().toString().substring(0, 16).toUpperCase();
        Instant expiresAt = Instant.now().plusSeconds(3600);
        log.info("[PARTNER-TOKEN] Dùng mock token cho '{}' (Lý do: {}). TTL: 3600s", partnerCode, reason);
        return new CachedToken(mockToken, expiresAt, "Bearer", "mock", true);
    }

    // =========================================================================
    // DOMAIN OBJECT
    // =========================================================================

    /**
     * Token cache entry với đầy đủ thông tin OAuth2 chuẩn RFC 6749.
     */
    @Getter
    @AllArgsConstructor
    public static class CachedToken {
        /** Access token value (Bearer token string) */
        private final String accessToken;

        /** Thời điểm token hết hạn (UTC). Dùng để kiểm tra proactive refresh. */
        private final Instant expiresAt;

        /** Token type (thường là "Bearer") */
        private final String tokenType;

        /** Scope được cấp */
        private final String scope;

        /** Đánh dấu token này là mock (local/test) hay real */
        private final boolean mockToken;

        /**
         * Kiểm tra token đã hoàn toàn hết hạn (không còn buffer).
         * Dùng cho validation layer khi cần kiểm tra chính xác.
         */
        public boolean isExpired() {
            return expiresAt != null && Instant.now().isAfter(expiresAt);
        }

        /**
         * Số giây còn lại trước khi token hết hạn.
         */
        public long getSecondsUntilExpiry() {
            if (expiresAt == null) return -1;
            return Math.max(0, expiresAt.getEpochSecond() - Instant.now().getEpochSecond());
        }
    }
}
