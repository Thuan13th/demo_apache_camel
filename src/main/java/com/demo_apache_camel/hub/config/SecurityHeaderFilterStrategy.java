package com.demo_apache_camel.hub.config;

import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultHeaderFilterStrategy;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * [SECURITY HEADER FILTER STRATEGY]
 * Chiến lược bảo mật chống rò rỉ dữ liệu (Anti-Data Leaking):
 * Lọc bỏ các HTTP headers nhạy cảm của Client (Authorization, Cookie, internal metadata)
 * trước khi Camel chuyển tiếp sang hệ thống đối tác bên ngoài.
 */
@Component("securityHeaderFilterStrategy")
public class SecurityHeaderFilterStrategy extends DefaultHeaderFilterStrategy {

    private static final Set<String> BLOCKED_OUTBOUND_HEADERS = Set.of(
            "authorization",
            "cookie",
            "set-cookie",
            "x-client-id",
            "x-client-secret",
            "x-internal-token",
            "x-user-id"
    );

    public SecurityHeaderFilterStrategy() {
        initialize();
    }

    private void initialize() {
        setLowerCase(true);
    }

    @Override
    public boolean applyFilterToCamelHeaders(String headerName, Object headerValue, Exchange exchange) {
        if (headerName != null && BLOCKED_OUTBOUND_HEADERS.contains(headerName.toLowerCase())) {
            return true; // Lọc bỏ không cho gửi ra ngoài
        }
        return super.applyFilterToCamelHeaders(headerName, headerValue, exchange);
    }
}
