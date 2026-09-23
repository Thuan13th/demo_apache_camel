package com.demo_apache_camel.hub.routes;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

/**
 * [HUB AUDIT LOG QUEUE]
 * Hàng đợi kiểm toán nội bộ của Hub (chạy ngầm trên worker thread riêng của SEDA).
 */
@Component
public class AuditRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        from("seda:hubAuditLog?concurrentConsumers=2")
            .routeId("hub-audit-log-route")
            .routeDescription("Lưu trữ nhật ký kiểm toán giao dịch của Hub bất đồng bộ")
            .log("[HUB-AUDIT] [Thread: ${threadName}] Lưu vết giao dịch OrderId=${exchangeProperty.OrderId}, Payload=${body}")
            .delay(300) // Giả lập độ trễ ghi vào Elasticsearch hoặc Audit Database
            .log("[HUB-AUDIT] Hoàn tất ghi nhận audit log cho OrderId=${exchangeProperty.OrderId}.");
    }
}
