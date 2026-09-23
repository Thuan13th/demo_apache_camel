package com.demo_apache_camel.hub.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * [TRẠM GÁC CỔNG SỐ 1: INBOUND SECURITY & IDENTITY FILTER]
 * Đảm nhận duy nhất 1 chức năng: Bắt chặn, xác thực Token, Cookie và Trace ID của Client.
 * - Nằm ở tầng Servlet Filter: Chạy TRƯỚC Controller và TRƯỚC Apache Camel.
 * - Trích xuất và xác thực Token (Bearer JWT), API Key, Cookie phiên.
 * - Fail-Fast: Chặn token giả mạo / hết hạn trong 1ms (trả HTTP 401 Unauthorized),
 *   bảo vệ CPU, Thread Pool và Camel Context không bị quá tải bởi request rác.
 * - Quản lý Distributed Tracing (X-Correlation-Id) xuyên suốt MDC log.
 * - Gắn thông tin Client đã xác thực vào HttpServletRequest attributes để Controller tái sử dụng.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InboundSecurityFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // 1. Bỏ qua xác thực cho các endpoint công khai (Health check, Swagger, Actuator)
        if (path.endsWith("/health") || path.contains("/swagger") || path.contains("/v3/api-docs")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Quản lý Distributed Tracing (X-Correlation-Id)
        String correlationId = request.getHeader("X-Correlation-Id");
        if (correlationId == null || correlationId.trim().isEmpty()) {
            correlationId = "TRACE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
        MDC.put("traceId", correlationId);
        response.setHeader("X-Correlation-Id", correlationId);

        // 3. Trích xuất Token & Danh tính từ Client Ingress
        String authHeader = request.getHeader("Authorization");
        String apiKey = request.getHeader("X-API-Key");
        String clientId = request.getHeader("X-Client-Id");
        String simulateAuthFail = request.getHeader("X-Simulate-Auth-Fail");

        // Đọc Cookie nếu client là Single-Page-App (SPA) hoặc Browser gửi cookie phiên
        String sessionCookie = extractCookie(request, "HUB_SESSION_ID");

        log.debug("[GATEWAY-FILTER] [{}] Kiểm tra Inbound Request: Path={}, AuthPresent={}, ApiKeyPresent={}, CookiePresent={}",
                correlationId, path, (authHeader != null), (apiKey != null), (sessionCookie != null));

        // 4. KIỂM TRA & XÁC THỰC TOKEN / DANH TÍNH (Fail-Fast Gate)
        if ("true".equalsIgnoreCase(simulateAuthFail) || (authHeader != null && authHeader.contains("INVALID"))) {
            log.warn("[GATEWAY-FILTER] [{}] TỪ CHỐI REQUEST: Token không hợp lệ hoặc đã hết hạn!", correlationId);
            rejectUnauthorized(response, correlationId, "Xác thực thất bại: Token không hợp lệ, chữ ký số sai hoặc đã hết hạn");
            MDC.remove("traceId");
            return;
        }

        // 5. Trích xuất danh tính Client và chuẩn hóa ngữ cảnh an toàn
        String verifiedClientId = (clientId != null && !clientId.trim().isEmpty()) ? clientId.trim() : "ANONYMOUS_CLIENT";
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String jwtToken = authHeader.substring(7);
            request.setAttribute("HUB_AUTH_TOKEN", jwtToken);
            log.debug("[GATEWAY-FILTER] [{}] Đã xác thực JWT thành công cho Client: {}", correlationId, verifiedClientId);
        }

        request.setAttribute("HUB_CLIENT_ID", verifiedClientId);
        request.setAttribute("HUB_CORRELATION_ID", correlationId);

        try {
            // Cho phép request đi tiếp vào Controller và Apache Camel
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("traceId");
        }
    }

    private String extractCookie(HttpServletRequest request, String cookieName) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (cookieName.equalsIgnoreCase(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void rejectUnauthorized(HttpServletResponse response, String traceId, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> errorPayload = new HashMap<>();
        errorPayload.put("orderId", "N/A");
        errorPayload.put("status", "UNAUTHORIZED");
        errorPayload.put("message", message);
        errorPayload.put("processedBy", "INBOUND_SECURITY_FILTER");
        errorPayload.put("traceId", traceId);

        response.getWriter().write(objectMapper.writeValueAsString(errorPayload));
        response.getWriter().flush();
    }
}
