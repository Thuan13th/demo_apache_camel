package com.demo_apache_camel;

import com.demo_apache_camel.hub.controller.OrderController;
import com.demo_apache_camel.hub.config.SecurityHeaderFilterStrategy;
import com.demo_apache_camel.hub.model.OrderRequest;
import com.demo_apache_camel.hub.model.OrderResponse;
import com.demo_apache_camel.hub.security.InboundSecurityFilter;
import com.demo_apache_camel.partner.common.PartnerTokenManager;
import org.apache.camel.ProducerTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * [TẬP HỢP BÀI TEST KIỂM THỬ TRỌNG TÂM CHO SERVICE HUB]
 * Kiểm tra đầy đủ 5 trụ cột kiến trúc cốt lõi:
 * 1. Content-Based Routing & Mediation (Viettel discount, Bill)
 * 2. Scatter-Gather Parallel Multicast & Aggregation (Vietjet vs VNA)
 * 3. Resilience, Retry & Napas Fallback
 * 4. Idempotency Guard (Chống xử lý trùng lặp giao dịch)
 * 5. Inbound Security (401 Fail-fast) & Outbound Sanitization & Token Lifecycle
 */
@SpringBootTest
public class CamelRouteTest {

    @Autowired
    private ProducerTemplate producerTemplate;

    @Autowired
    private OrderController orderController;

    @Autowired
    private PartnerTokenManager partnerTokenManager;

    @Autowired
    private SecurityHeaderFilterStrategy securityHeaderFilterStrategy;

    @Autowired
    private InboundSecurityFilter inboundSecurityFilter;

    @Test
    @DisplayName("1. Content-Based Router: Nạp thẻ Viettel chiết khấu 5%")
    void testTopupViettelRoute() {
        OrderRequest req = new OrderRequest("TEST-VT-01", "Nguyễn Văn A", "TOPUP", "VIETTEL", 100000.0, null);
        OrderResponse response = producerTemplate.requestBody("direct:processOrder", req, OrderResponse.class);

        assertNotNull(response);
        assertEquals("SUCCESS", response.getStatus());
        assertEquals("VIETTEL_TELCO_DIRECT", response.getProcessedBy());
        assertEquals(95000.0, response.getFinalAmount(), "Chiết khấu 5% của 100,000 VND là 95,000 VND");
    }

    @Test
    @DisplayName("2. Scatter-Gather EIP: Tìm vé máy bay song song và chọn vé rẻ nhất")
    void testFlightScatterGatherRoute() {
        OrderRequest req = new OrderRequest("TEST-FL-02", "Trần Thị B", "FLIGHT", null, 0.0, "HAN");
        OrderResponse response = producerTemplate.requestBody("direct:processOrder", req, OrderResponse.class);

        assertNotNull(response);
        assertEquals("SUCCESS", response.getStatus());
        // Vietjet báo giá 1,450,000 VND còn VNA báo giá 1,850,000 VND => Aggregator phải chọn Vietjet
        assertEquals("Vietjet Air", response.getProcessedBy());
        assertEquals(1450000.0, response.getFinalAmount());
        assertTrue(response.getMessage().contains("VJ-152"));
    }

    @Test
    @DisplayName("3. Booking Gateway: Đặt và giữ chỗ vé máy bay")
    void testFlightBookingRoute() {
        OrderRequest req = new OrderRequest("TEST-BOOK-01", "Trần Quốc Tuấn", "FLIGHT_BOOKING", "VIETJET", 1450000.0, "HAN");
        OrderResponse response = producerTemplate.requestBody("direct:processOrder", req, OrderResponse.class);

        assertNotNull(response);
        assertEquals("SUCCESS", response.getStatus());
        assertEquals("AIRLINE_BOOKING_GATEWAY", response.getProcessedBy());
        assertTrue(response.getMessage().contains("Mã PNR:"));
    }

    @Test
    @DisplayName("4. Resilience & Error Handling: Retry khi mạng lỗi và Fallback sang Napas")
    void testTopupRetryAndFallback() {
        // Amount = 999999 kích hoạt lỗi giả lập tại Vinaphone
        OrderRequest req = new OrderRequest("TEST-RETRY-03", "Lê Văn C", "TOPUP", "VINA", 999999.0, null);
        OrderResponse response = producerTemplate.requestBody("direct:processOrder", req, OrderResponse.class);

        assertNotNull(response);
        assertEquals("FALLBACK_SUCCESS", response.getStatus());
        assertEquals("NAPAS_FALLBACK_GATEWAY", response.getProcessedBy());
    }

    @Test
    @DisplayName("5. Content-Based Router: Dịch vụ ngoài danh mục bị từ chối")
    void testUnsupportedService() {
        OrderRequest req = new OrderRequest("TEST-UNKNOWN-04", "Phạm Văn D", "HOTEL_BOOKING", null, 500000.0, null);
        OrderResponse response = producerTemplate.requestBody("direct:processOrder", req, OrderResponse.class);

        assertNotNull(response);
        assertEquals("REJECTED", response.getStatus());
        assertEquals("HUB_SERVICE_REGISTRY", response.getProcessedBy());
    }

    @Test
    @DisplayName("6. Bill Mediation: Thanh toán hóa đơn dịch vụ")
    void testBillService() {
        OrderRequest req = new OrderRequest("TEST-BILL-05", "Vũ Hoàng", "BILL", "EVN_HANOI", 450000.0, null);
        OrderResponse response = producerTemplate.requestBody("direct:processOrder", req, OrderResponse.class);

        assertNotNull(response);
        assertEquals("SUCCESS", response.getStatus());
        assertEquals("EVN_HANOI", response.getProcessedBy());
    }

    @Test
    @DisplayName("7. Ingress Controller: Tiếp nhận đơn, tra cứu và kiểm tra sức khỏe")
    void testOrderControllerIntegration() {
        // A. Process Order
        OrderRequest req = new OrderRequest("CTRL-TEST-01", "Trần Văn E", "TOPUP", "VIETTEL", 50000.0, null);
        ResponseEntity<OrderResponse> response = orderController.processOrder(
                "POSTMAN_TEST", "MY-CUSTOM-CORR-123", null, req);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("MY-CUSTOM-CORR-123", response.getHeaders().getFirst("X-Correlation-Id"));
        assertNotNull(response.getBody());
        assertEquals("SUCCESS", response.getBody().getStatus());

        // B. Inquiry Status
        ResponseEntity<OrderResponse> statusResp = orderController.getOrderStatus("ORD-2026-9999", "TRACE-CHK-01");
        assertNotNull(statusResp);
        assertEquals(HttpStatus.OK, statusResp.getStatusCode());
        assertEquals("QUERY_STATUS", statusResp.getBody().getServiceType());

        // C. Health check
        ResponseEntity<?> healthResp = orderController.healthCheck();
        assertEquals(HttpStatus.OK, healthResp.getStatusCode());
    }

    @Test
    @DisplayName("8. Fail-Fast Validation: Chặn request rỗng ngay tại Controller")
    void testOrderControllerValidation() {
        ResponseEntity<OrderResponse> response = orderController.processOrder("TEST", null, null, null);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("FAILED", response.getBody().getStatus());
    }

    @Test
    @DisplayName("9. Idempotency Guard: Chặn xử lý trùng lặp với cùng OrderId")
    void testIdempotencyDuplicateOrder() {
        OrderRequest req = new OrderRequest("DUP-ORDER-999", "Lê Hoàng", "TOPUP", "VIETTEL", 50000.0, null);

        // Lần 1: Thành công
        OrderResponse resp1 = producerTemplate.requestBody("direct:processOrder", req, OrderResponse.class);
        assertNotNull(resp1);
        assertEquals("SUCCESS", resp1.getStatus());

        // Lần 2: Trùng lặp -> Idempotency Guard chặn lại với status REJECTED
        OrderResponse resp2 = producerTemplate.requestBody("direct:processOrder", req, OrderResponse.class);
        assertNotNull(resp2);
        assertEquals("REJECTED", resp2.getStatus());
        assertEquals("HUB_IDEMPOTENCY_GUARD", resp2.getProcessedBy());
    }

    @Test
    @DisplayName("10. Security Header Filter: Lọc bỏ Authorization và Cookie trước khi ra ngoài")
    void testSecurityHeaderFilter() {
        assertTrue(securityHeaderFilterStrategy.applyFilterToCamelHeaders("Authorization", "Bearer secret", null));
        assertTrue(securityHeaderFilterStrategy.applyFilterToCamelHeaders("Cookie", "JSESSIONID=123", null));
        assertTrue(securityHeaderFilterStrategy.applyFilterToCamelHeaders("X-Client-Secret", "secret-key", null));
        assertFalse(securityHeaderFilterStrategy.applyFilterToCamelHeaders("Content-Type", "application/json", null));
    }

    @Test
    @DisplayName("11. Inbound Security Filter: Chặn 401 Unauthorized khi Token lỗi")
    void testInboundSecurityFilter_Unauthorized() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders");
        request.addHeader("Authorization", "Bearer INVALID_TOKEN_123");
        request.addHeader("X-Correlation-Id", "TRACE-AUTH-FAIL");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        inboundSecurityFilter.doFilter(request, response, filterChain);

        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatus());
        assertTrue(response.getContentAsString().contains("UNAUTHORIZED"));
        assertTrue(response.getContentAsString().contains("INBOUND_SECURITY_FILTER"));
        assertEquals("TRACE-AUTH-FAIL", response.getHeader("X-Correlation-Id"));
    }

    @Test
    @DisplayName("12. Inbound Security Filter: Cho phép Token hợp lệ đi tiếp và gắn danh tính Client")
    void testInboundSecurityFilter_Success() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders");
        request.addHeader("Authorization", "Bearer valid.jwt.signature");
        request.addHeader("X-Client-Id", "TRUSTED_MOBILE_APP");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        inboundSecurityFilter.doFilter(request, response, filterChain);

        assertEquals(HttpStatus.OK.value(), response.getStatus());
        assertEquals("TRUSTED_MOBILE_APP", request.getAttribute("HUB_CLIENT_ID"));
        assertNotNull(request.getAttribute("HUB_CORRELATION_ID"));
    }

    @Test
    @DisplayName("13. Partner Token Lifecycle: Quản lý cache và tự động refresh khi nhận lỗi 401")
    void testPartnerTokenManagerLifecycle() {
        String token1 = partnerTokenManager.getOrRefreshToken("VIETJET");
        assertNotNull(token1);

        // Lấy lại ngay: Phải trả về token từ Cache
        String tokenCached = partnerTokenManager.getOrRefreshToken("VIETJET");
        assertEquals(token1, tokenCached);

        // Giả lập nhận lỗi 401 từ Đối tác -> Thu hồi token
        partnerTokenManager.evictToken("VIETJET");

        // Gọi lại: TokenManager tự động cấp phát token mới
        String tokenNew = partnerTokenManager.getOrRefreshToken("VIETJET");
        assertNotNull(tokenNew);
    }
}
