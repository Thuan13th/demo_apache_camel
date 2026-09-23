package com.demo_apache_camel.hub.controller;

import com.demo_apache_camel.hub.model.OrderRequest;
import com.demo_apache_camel.hub.model.OrderResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.ProducerTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * [HUB API GATEWAY CONTROLLER - CHUẨN DOANH NGHIỆP PRODUCTION-GRADE]
 * Cổng API Gateway "Một Cửa Duy Nhất" (Single-Entry Ingress) của Hub:
 * - Áp dụng triết lý Skinny Controller: Tuyệt đối không chứa logic nghiệp vụ, ủy quyền 100% cho Apache Camel.
 * - Distributed Tracing: Tự động cấp phát và lan truyền X-Correlation-Id cho mọi giao dịch.
 * - Fail-Fast Validation: Chặn request rác/thiếu trường ngay tại cửa trong 1ms.
 * - RESTful Semantic Status Codes: Phản hồi chuẩn xác mã HTTP (200, 400, 422, 502).
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/v1/orders", "/api/orders"})
public class OrderController {

    private final ProducerTemplate producerTemplate;

    /**
     * 1. CORE INGRESS: Tiếp nhận và điều phối mọi loại giao dịch từ Client (Postman/Mobile/B2B)
     * Áp dụng nguyên lý "Một Cửa Duy Nhất" - Phân luồng hoàn toàn tự động qua Apache Camel.
     */
    @PostMapping
    public ResponseEntity<OrderResponse> processOrder(
            @RequestHeader(value = "X-Client-Id", required = false, defaultValue = "EXTERNAL_CLIENT") String clientId,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestHeader(value = "X-Simulate-Error", required = false) String simulateError,
            @RequestBody(required = false) OrderRequest request) {

        // 1. Quản lý Trace ID phân tán (Distributed Tracing)
        final String traceId = (correlationId != null && !correlationId.trim().isEmpty())
                ? correlationId.trim()
                : "TRACE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.add("X-Correlation-Id", traceId);

        // 2. Fail-Fast Validation: Chặn rác ngay tại cửa trước khi tạo Camel Exchange
        if (request == null || request.getServiceType() == null || request.getServiceType().trim().isEmpty()) {
            log.warn("[HTTP-INGRESS] [{}] Từ chối request: Payload rỗng hoặc thiếu serviceType", traceId);
            OrderResponse err = OrderResponse.builder()
                    .orderId(request != null ? request.getOrderId() : "N/A")
                    .status("FAILED")
                    .message("Yêu cầu không hợp lệ: Cần cung cấp đầy đủ thông tin đơn hàng và loại dịch vụ (serviceType)")
                    .processedBy("HUB_API_GATEWAY")
                    .build();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).headers(responseHeaders).body(err);
        }

        log.info("[HTTP-INGRESS] [{}] Nhận request từ Client '{}' [OrderId: {}, Dịch vụ: {}, Khách: {}]",
                traceId, clientId, request.getOrderId(), request.getServiceType(), request.getCustomerName());

        // 3. Đóng gói Header & Payload đẩy vào luồng điều phối trung tâm của Camel
        Map<String, Object> camelHeaders = new HashMap<>();
        camelHeaders.put("CamelHttpMethod", "POST");
        camelHeaders.put("X-Client-Id", clientId);
        camelHeaders.put("X-Correlation-Id", traceId);
        if (simulateError != null) {
            camelHeaders.put("X-Simulate-Error", simulateError);
        }

        OrderResponse response = producerTemplate.requestBodyAndHeaders(
                "direct:processOrder",
                request,
                camelHeaders,
                OrderResponse.class
        );

        // 4. Map HTTP Status Code chuẩn RESTful theo kết quả Camel trả về
        HttpStatus httpStatus = HttpStatus.OK;
        if (response != null && "REJECTED".equalsIgnoreCase(response.getStatus())) {
            httpStatus = HttpStatus.UNPROCESSABLE_ENTITY; // 422: Dịch vụ bị từ chối
        } else if (response != null && "FAILED".equalsIgnoreCase(response.getStatus())) {
            httpStatus = HttpStatus.BAD_GATEWAY;          // 502: Đối tác lỗi
        }

        log.info("[HTTP-INGRESS] [{}] Trả kết quả: OrderId={}, Status={}, HTTP={}",
                traceId,
                response != null ? response.getOrderId() : "N/A",
                response != null ? response.getStatus() : "NULL",
                httpStatus.value());

        return ResponseEntity.status(httpStatus).headers(responseHeaders).body(response);
    }

    /**
     * 2. INQUIRY: Tra cứu trạng thái đơn hàng (Check Trans / Đối soát)
     * Dành cho Client kiểm tra lại đơn khi xảy ra sự cố mạng, tránh nạp/mua trùng.
     */
    @GetMapping("/{orderId}/status")
    public ResponseEntity<OrderResponse> getOrderStatus(
            @PathVariable("orderId") String orderId,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {

        final String traceId = (correlationId != null && !correlationId.trim().isEmpty())
                ? correlationId.trim()
                : "QUERY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.add("X-Correlation-Id", traceId);

        log.info("[HTTP-INQUIRY] [{}] Tra cứu trạng thái đơn hàng: {}", traceId, orderId);

        OrderResponse response = producerTemplate.requestBody(
                "direct:queryOrderStatus",
                orderId,
                OrderResponse.class
        );

        return ResponseEntity.ok().headers(responseHeaders).body(response);
    }

    /**
     * 3. DEVOPS & MONITORING: Kiểm tra sức khỏe hệ thống (Kubernetes Probe)
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> info = new HashMap<>();
        info.put("status", "UP");
        info.put("service", "Enterprise Integration Hub");
        info.put("engine", "Apache Camel 4.x + Spring Boot");
        return ResponseEntity.ok(info);
    }
}


