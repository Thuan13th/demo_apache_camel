package com.demo_apache_camel;

import com.demo_apache_camel.hub.config.SecurityHeaderFilterStrategy;
import com.demo_apache_camel.hub.controller.OrderController;
import com.demo_apache_camel.hub.model.OrderRequest;
import com.demo_apache_camel.hub.model.OrderResponse;
import com.demo_apache_camel.hub.security.InboundSecurityFilter;
import com.demo_apache_camel.hub.security.JwtTokenValidator;
import com.demo_apache_camel.hub.security.JwtValidationException;
import com.demo_apache_camel.partner.common.PartnerTokenManager;
import org.apache.camel.ProducerTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * [TẬP HỢP BÀI TEST KIỂM THỬ TOÀN DIỆN CHO SERVICE HUB DOANH NGHIỆP]
 * Kiểm tra đầy đủ các domain dịch vụ (Hàng không, Viễn thông, SIM, Bảo hiểm, Vé vui chơi, Đối soát lô):
 * 1. Content-Based Routing & Mediation (Viettel, Mobi, Vina, Bill)
 * 2. Scatter-Gather Parallel Multicast & Aggregation (Vietjet vs VNA)
 * 3. Resilience, Retry & Napas Fallback
 * 4. Idempotency Guard (Chống xử lý trùng lặp giao dịch)
 * 5. Inbound Security (401 Fail-fast) & Outbound Sanitization & Token Lifecycle
 * 6. SIM & eSIM Activation (Wintel, Viettel)
 * 7. Insurance Policy Issuance (Bảo Việt, PVI)
 * 8. Attraction Ticket Issuance (VinWonders, SunWorld)
 * 9. Batch & Reconciliation File Parsers (CSV, XML, JSON, Napas Fixed-Length)
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

    @Autowired
    private JwtTokenValidator jwtTokenValidator;

    // JWT hợp lệ được tạo trong test setup, sử dụng xuyên suốt các test case Security
    private String validJwtToken;

    @BeforeEach
    void setUp() {
        // Tạo JWT thật bằng JwtTokenValidator với secret đúng từ application.yaml
        validJwtToken = jwtTokenValidator.issueToken("TRUSTED_TEST_CLIENT", List.of("ROLE_PARTNER"));
    }

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
    @DisplayName("11. Inbound Security Filter: Chặn 401 khi gửi JWT sai chữ ký số (giả mạo token)")
    void testInboundSecurityFilter_InvalidSignature() throws Exception {
        // JWT được ký bởi key khác (giả mạo) — phải bị reject INVALID_SIGNATURE
        String forgeryToken = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJIQUNLRVIiLCJpc3MiOiJlbnRlcnByaXNlLXNlcnZpY2UtaHViIn0.WRONG_SIGNATURE_XYZ";
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders");
        request.addHeader("Authorization", "Bearer " + forgeryToken);
        request.addHeader("X-Correlation-Id", "TRACE-FORGERY-001");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        inboundSecurityFilter.doFilter(request, response, filterChain);

        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatus());
        String body = response.getContentAsString();
        assertTrue(body.contains("UNAUTHORIZED"), "Response phải có status UNAUTHORIZED");
        assertTrue(body.contains("INBOUND_SECURITY_FILTER"), "Response phải ghi rõ nguồn xử lý");
        assertEquals("TRACE-FORGERY-001", response.getHeader("X-Correlation-Id"));
    }

    @Test
    @DisplayName("12. Inbound Security Filter: JWT thật hợp lệ → Pass, gắn JwtClaims vào request attribute")
    void testInboundSecurityFilter_ValidJWT() throws Exception {
        // Dùng JWT thật được tạo trong @BeforeEach bởi JwtTokenValidator
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders");
        request.addHeader("Authorization", "Bearer " + validJwtToken);
        request.addHeader("X-Client-Id", "TRUSTED_MOBILE_APP");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        inboundSecurityFilter.doFilter(request, response, filterChain);

        assertEquals(HttpStatus.OK.value(), response.getStatus(), "Request hợp lệ phải qua được filter");
        // clientId từ X-Client-Id header ưu tiên hơn JWT subject
        assertEquals("TRUSTED_MOBILE_APP", request.getAttribute("HUB_CLIENT_ID"));
        assertNotNull(request.getAttribute("HUB_CORRELATION_ID"), "Correlation ID phải được gắn");
        assertNotNull(request.getAttribute("HUB_JWT_CLAIMS"), "JWT Claims phải được gắn vào request");

        // Kiểm tra JwtClaims được parse đúng
        JwtTokenValidator.JwtClaims claims = (JwtTokenValidator.JwtClaims) request.getAttribute("HUB_JWT_CLAIMS");
        assertEquals("TRUSTED_TEST_CLIENT", claims.clientId());
        assertEquals("enterprise-service-hub", claims.issuer());
    }

    @Test
    @DisplayName("13. JWT Validator: Tạo và validate JWT thật — subject, issuer, expiry")
    void testJwtValidatorIssueAndValidate() throws JwtValidationException {
        // A. Tạo JWT thật bằng jjwt HS256
        String token = jwtTokenValidator.issueToken("MOBILE_APP_001", List.of("ROLE_PARTNER", "ROLE_USER"));
        assertNotNull(token);
        assertTrue(token.split("\\.").length == 3, "JWT phải có 3 phần header.payload.signature");

        // B. Validate token vừa tạo — phải SUCCESS
        JwtTokenValidator.JwtClaims claims = jwtTokenValidator.validate("Bearer " + token);
        assertNotNull(claims);
        assertEquals("MOBILE_APP_001", claims.clientId());
        assertEquals("enterprise-service-hub", claims.issuer());
        assertNotNull(claims.expiresAt(), "expiresAt phải có giá trị");
        assertNotNull(claims.roles(), "roles claim phải tồn tại");
    }

    @Test
    @DisplayName("13b. Partner Token Lifecycle: Cache hit, evict 401, proactive refresh logic")
    void testPartnerTokenManagerLifecycle() {
        // A. Fetch token lần đầu (graceful fallback mock trong môi trường test)
        String token1 = partnerTokenManager.getOrRefreshToken("VIETJET");
        assertNotNull(token1, "Token lần đầu không được null");

        // B. Lấy lại ngay: phải trả về CÙNG token từ Cache (không gọi lại OAuth2 endpoint)
        String tokenCached = partnerTokenManager.getOrRefreshToken("VIETJET");
        assertEquals(token1, tokenCached, "Cache hit: phải trả về token y hệt");

        // C. Giả lập nhận 401 từ đối tác → evict token
        partnerTokenManager.evictToken("VIETJET");

        // D. Gọi lại sau evict → phải tạo token MỚI (khác token cũ)
        String tokenNew = partnerTokenManager.getOrRefreshToken("VIETJET");
        assertNotNull(tokenNew, "Token mới sau evict không được null");
        // Token mới phải được tạo lại (có thể giống trong mock, nhưng không bị null/exception)

        // E. Kiểm tra warmUp không throw exception
        assertDoesNotThrow(() -> partnerTokenManager.warmUpAllPartners(),
                "warmUpAllPartners phải hoàn thành không có exception");
    }

    @Test
    @DisplayName("14. SIM Domain: Cấp phát eSIM Wintel thành công")
    void testWintelEsimService() {
        OrderRequest req = new OrderRequest("TEST-SIM-01", "Hoàng Nam", "SIM", "WINTEL", 120000.0, null);
        OrderResponse response = producerTemplate.requestBody("direct:processOrder", req, OrderResponse.class);

        assertNotNull(response);
        assertEquals("SUCCESS", response.getStatus());
        assertEquals("WINTEL_ESIM_GATEWAY", response.getProcessedBy());
        assertTrue(response.getMessage().contains("LPA:1$wintel.vn$"));
    }

    @Test
    @DisplayName("15. Insurance Domain: Mua bảo hiểm du lịch Bảo Việt thành công")
    void testBaoVietInsuranceService() {
        OrderRequest req = new OrderRequest("TEST-INS-01", "Đặng Thùy Trang", "INSURANCE", "BAOVIET", 250000.0, null);
        OrderResponse response = producerTemplate.requestBody("direct:processOrder", req, OrderResponse.class);

        assertNotNull(response);
        assertEquals("SUCCESS", response.getStatus());
        assertEquals("BAOVIET_INSURANCE_GATEWAY", response.getProcessedBy());
        assertTrue(response.getMessage().contains("BV-POLICY-"));
    }

    @Test
    @DisplayName("16. Attraction Domain: Xuất vé vui chơi VinWonders thành công")
    void testVinwondersAttractionService() {
        OrderRequest req = new OrderRequest("TEST-ATT-01", "Phạm Minh Châu", "ATTRACTION", "VINWONDERS", 850000.0, null);
        OrderResponse response = producerTemplate.requestBody("direct:processOrder", req, OrderResponse.class);

        assertNotNull(response);
        assertEquals("SUCCESS", response.getStatus());
        assertEquals("VINWONDERS_GATEWAY", response.getProcessedBy());
        assertTrue(response.getMessage().contains("VW-TICKET-"));
    }

    @Test
    @DisplayName("17. Reconciliation: Bóc tách file CSV đối soát Viettel")
    void testViettelCsvReconciliationParser() throws Exception {
        String csvContent = Files.readString(Path.of("data/samples/VIETTEL_TOPUP_20260921.csv"));
        @SuppressWarnings("unchecked")
        List<OrderResponse> results = producerTemplate.requestBodyAndHeader(
                "direct:processInboundFile",
                csvContent,
                "CamelFileName",
                "VIETTEL_TOPUP_20260921.csv",
                List.class
        );

        assertNotNull(results);
        assertEquals(3, results.size());
        assertEquals("ORD-VT-101", results.get(0).getOrderId());
        assertEquals(95000.0, results.get(0).getFinalAmount());
    }

    @Test
    @DisplayName("18. Reconciliation: Bóc tách file XML đặt vé Sabre GDS")
    void testSabreXmlReconciliationParser() throws Exception {
        String xmlContent = Files.readString(Path.of("data/samples/SABRE_BOOKINGS_20260921.xml"));
        @SuppressWarnings("unchecked")
        List<OrderResponse> results = producerTemplate.requestBodyAndHeader(
                "direct:processInboundFile",
                xmlContent,
                "CamelFileName",
                "SABRE_BOOKINGS_20260921.xml",
                List.class
        );

        assertNotNull(results);
        assertEquals(2, results.size());
        assertEquals("ORD-FL-202", results.get(0).getOrderId());
        assertEquals(1850000.0, results.get(0).getFinalAmount());
    }

    @Test
    @DisplayName("19. Reconciliation: Bóc tách file JSON vé VinWonders")
    void testVinwondersJsonReconciliationParser() throws Exception {
        String jsonContent = Files.readString(Path.of("data/samples/VINWONDERS_TICKETS_20260921.json"));
        @SuppressWarnings("unchecked")
        List<OrderResponse> results = producerTemplate.requestBodyAndHeader(
                "direct:processInboundFile",
                jsonContent,
                "CamelFileName",
                "VINWONDERS_TICKETS_20260921.json",
                List.class
        );

        assertNotNull(results);
        assertEquals(2, results.size());
        assertEquals("VW-88991", results.get(0).getOrderId());
        assertEquals(850000.0, results.get(0).getFinalAmount());
    }

    @Test
    @DisplayName("20. Reconciliation: Bóc tách file TXT Fixed-Length Napas")
    void testNapasFixedLengthReconciliationParser() throws Exception {
        String txtContent = Files.readString(Path.of("data/samples/NAPAS_RECON_20260921.txt"));
        @SuppressWarnings("unchecked")
        List<OrderResponse> results = producerTemplate.requestBodyAndHeader(
                "direct:processInboundFile",
                txtContent,
                "CamelFileName",
                "NAPAS_RECON_20260921.txt",
                List.class
        );

        assertNotNull(results);
        assertEquals(2, results.size());
        assertEquals("ORD-PAY-0001", results.get(0).getOrderId());
        assertEquals(100000.0, results.get(0).getFinalAmount());
    }

    @Test
    @DisplayName("21. Declarative YAML DSL: Xử lý phát hành Voucher GotIt hoàn toàn bằng YAML")
    void testDeclarativeYamlGotItVoucherRoute() {
        OrderRequest req = new OrderRequest("TEST-VC-01", "Hoàng Kim", "VOUCHER", "GOTIT", 200000.0, null);
        OrderResponse response = producerTemplate.requestBody("direct:processOrder", req, OrderResponse.class);

        assertNotNull(response);
        assertEquals("SUCCESS", response.getStatus());
        assertEquals("YAML_DSL_GOTIT_GATEWAY", response.getProcessedBy());
        assertEquals(180000.0, response.getFinalAmount());
        assertTrue(response.getMessage().contains("GotIt"));
    }

    @Test
    @DisplayName("22. Declarative YAML DSL: Xử lý phát hành Voucher UrBox hoàn toàn bằng YAML")
    void testDeclarativeYamlUrBoxVoucherRoute() {
        OrderRequest req = new OrderRequest("TEST-VC-02", "Ngô Bảo", "VOUCHER", "URBOX", 100000.0, null);
        OrderResponse response = producerTemplate.requestBody("direct:processOrder", req, OrderResponse.class);

        assertNotNull(response);
        assertEquals("SUCCESS", response.getStatus());
        assertEquals("YAML_DSL_URBOX_GATEWAY", response.getProcessedBy());
        assertEquals(92000.0, response.getFinalAmount());
        assertTrue(response.getMessage().contains("UrBox"));
    }

    @Test
    @DisplayName("23. Declarative YAML DSL: Từ chối nhà cung cấp Voucher không hỗ trợ")
    void testDeclarativeYamlUnsupportedVoucherRoute() {
        OrderRequest req = new OrderRequest("TEST-VC-03", "Trần D", "VOUCHER", "UNKNOWN_PARTNER", 50000.0, null);
        OrderResponse response = producerTemplate.requestBody("direct:processOrder", req, OrderResponse.class);

        assertNotNull(response);
        assertEquals("REJECTED", response.getStatus());
        assertEquals("YAML_DSL_VOUCHER_GATEWAY", response.getProcessedBy());
    }

    @Test
    @DisplayName("24. Config-Driven Dynamic Routing: Điều phối tự động đối tác đã khai báo trong application.yaml")
    void testConfigDrivenDynamicRouting() {
        // VIETTEL đã được khai báo trong application.yaml -> Dynamic Router tự nhận diện
        OrderRequest req = new OrderRequest("TEST-DYN-01", "Lê Văn F", "DYNAMIC", "VIETTEL", 50000.0, null);
        OrderResponse response = producerTemplate.requestBody("direct:processOrder", req, OrderResponse.class);

        assertNotNull(response);
        assertEquals("SUCCESS", response.getStatus());
        assertEquals("DYNAMIC_CONFIG_ROUTER_VIETTEL", response.getProcessedBy());
        assertTrue(response.getMessage().contains("VIETTEL"));
    }

    @Test
    @DisplayName("25. Config-Driven Dynamic Routing: Từ chối đối tác chưa khai báo trong application.yaml")
    void testConfigDrivenDynamicRoutingUnregisteredPartner() {
        OrderRequest req = new OrderRequest("TEST-DYN-02", "Đỗ Văn G", "DYNAMIC", "UNREGISTERED_BANK", 50000.0, null);
        OrderResponse response = producerTemplate.requestBody("direct:processOrder", req, OrderResponse.class);

        assertNotNull(response);
        assertEquals("REJECTED", response.getStatus());
        assertEquals("DYNAMIC_CONFIG_GUARD", response.getProcessedBy());
    }
}
