package com.demo_apache_camel.partner.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * [PARTNER TOKEN PROACTIVE REFRESH SCHEDULER]
 *
 * Chạy ngầm định kỳ (mỗi 60 giây) để:
 * 1. Kiểm tra tất cả token đối tác trong cache.
 * 2. Tự động warm-up token trước khi hết hạn (proactive refresh).
 *
 * Lợi ích so với reactive refresh (chờ 401):
 * - Zero downtime: Token luôn sẵn sàng, không có request nào phải chờ fetch token.
 * - Giảm latency spike: Tránh 1 request "tốt số" phải gánh toàn bộ thời gian fetch token.
 * - Fail-fast detection: Phát hiện sớm nếu đối tác OAuth2 endpoint có sự cố.
 *
 * Lưu ý:
 * - Cần @EnableScheduling trong class cấu hình (DemoApacheCamelApplication hoặc CamelGlobalConfig).
 * - Mặc định chạy sau 30s khởi động (initialDelay) để tránh startup conflict.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PartnerTokenRefreshScheduler {

    private final PartnerTokenManager partnerTokenManager;

    /**
     * Scheduled task: Proactively refresh partner tokens mỗi 60 giây.
     * - initialDelay = 30_000ms: Chờ 30s sau khởi động mới bắt đầu (để Spring context fully loaded).
     * - fixedDelay = 60_000ms: Chạy lại 60s sau mỗi lần hoàn thành (không phải fixed rate).
     */
    @Scheduled(initialDelay = 30_000, fixedDelay = 60_000)
    public void proactiveTokenRefresh() {
        log.debug("[TOKEN-SCHEDULER] Bắt đầu kiểm tra và proactive refresh token đối tác lúc {}",
                LocalDateTime.now());

        AtomicInteger refreshedCount = new AtomicInteger(0);
        AtomicInteger skippedCount = new AtomicInteger(0);

        // Lấy snapshot hiện tại để kiểm tra, không lock toàn bộ store
        Map<String, PartnerTokenManager.CachedToken> snapshot = partnerTokenManager.getTokenSnapshot();

        if (snapshot.isEmpty()) {
            // Chưa có token nào trong cache → warm up tất cả partners đã cấu hình
            log.info("[TOKEN-SCHEDULER] Cache rỗng, tiến hành warm-up tất cả partners...");
            partnerTokenManager.warmUpAllPartners();
            return;
        }

        snapshot.forEach((partnerCode, cachedToken) -> {
            long secondsLeft = cachedToken.getSecondsUntilExpiry();
            if (secondsLeft < 0) {
                log.warn("[TOKEN-SCHEDULER] Token '{}' đã hết hạn, buộc refresh ngay!", partnerCode);
                partnerTokenManager.getOrRefreshToken(partnerCode);
                refreshedCount.incrementAndGet();
            } else {
                // getOrRefreshToken sẽ tự quyết định refresh hay không dựa trên buffer
                String freshToken = partnerTokenManager.getOrRefreshToken(partnerCode);
                // Kiểm tra xem có refresh hay không dựa trên sự thay đổi
                if (!freshToken.equals(cachedToken.getAccessToken())) {
                    log.info("[TOKEN-SCHEDULER] Token '{}' đã được refresh proactively (còn {}s)",
                            partnerCode, secondsLeft);
                    refreshedCount.incrementAndGet();
                } else {
                    log.debug("[TOKEN-SCHEDULER] Token '{}' còn hợp lệ (còn {}s), bỏ qua",
                            partnerCode, secondsLeft);
                    skippedCount.incrementAndGet();
                }
            }
        });

        if (refreshedCount.get() > 0 || snapshot.size() > 0) {
            log.info("[TOKEN-SCHEDULER] Hoàn tất: {} token được refresh, {} token còn hợp lệ",
                    refreshedCount.get(), skippedCount.get());
        }
    }
}
