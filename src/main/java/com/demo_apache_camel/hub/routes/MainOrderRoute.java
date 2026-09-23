package com.demo_apache_camel.hub.routes;

import com.demo_apache_camel.hub.model.OrderRequest;
import com.demo_apache_camel.hub.model.OrderResponse;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * [HUB CENTRAL ORCHESTRATOR]
 * Tuyến điều phối trung tâm của Hub:
 * 1. Tiếp nhận mọi yêu cầu đã được chuẩn hóa về OrderRequest.
 * 2. Cấp phát OrderId nội bộ và lưu vào Exchange Property.
 * 3. Kích hoạt WireTap bắn bản sao sang hàng đợi Audit Log ngầm của Hub.
 * 4. Content-Based Router phân luồng sang các domain đối tác:
 *    - TOPUP: Nạp thẻ viễn thông (Viettel, Mobi, Vina, Napas)
 *    - FLIGHT: Khảo giá vé máy bay đa hãng (Scatter-Gather)
 *    - FLIGHT_BOOKING: Đặt và giữ chỗ vé máy bay (PNR)
 *    - SIM: Đấu nối SIM & cấp mã QR eSIM (Wintel, Viettel)
 *    - INSURANCE: Bảo hiểm du lịch / tai nạn (Bảo Việt, PVI)
 *    - ATTRACTION / BILL: Vé tham quan vui chơi & thanh toán dịch vụ (VinWonders, SunWorld, EVN)
 */
@Component
public class MainOrderRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {

        from("direct:processOrder")
            .routeId("hub-main-order-orchestrator")
            .routeDescription("Bộ não điều phối trung tâm của Service Hub")

            // 1. Quản lý trạng thái và chuẩn hóa biến nội bộ của Hub
            .process(exchange -> {
                OrderRequest req = exchange.getMessage().getBody(OrderRequest.class);
                if (req == null) {
                    req = new OrderRequest();
                    exchange.getMessage().setBody(req);
                }
                if (req.getOrderId() == null || req.getOrderId().trim().isEmpty()) {
                    req.setOrderId("HUB-ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
                }
                // Lưu vào Exchange Property để sống suốt toàn bộ luồng
                exchange.setProperty("OrderId", req.getOrderId());
                exchange.setProperty("ServiceType", req.getServiceType());
            })

            .log("[HUB-CORE] Tiếp nhận yêu cầu [OrderId: ${exchangeProperty.OrderId}] - Dịch vụ: ${exchangeProperty.ServiceType}")

            // 2. WireTap EIP: Ghi vết kiểm toán ngầm bất đồng bộ
            .wireTap("seda:hubAuditLog")

            // 3. Content-Based Router: Phân luồng sang các Phân hệ Đối tác theo Domain
            .choice()
                .when(simple("${body.serviceType} == 'TOPUP'"))
                    .log("[HUB-CORE] => Chuyển tiếp sang Cổng Đối tác Viễn thông (Telco Partner Gateway)")
                    .to("direct:topupPartnerService")

                .when(simple("${body.serviceType} == 'FLIGHT'"))
                    .log("[HUB-CORE] => Chuyển tiếp sang Cổng Đối tác Hàng không (Tìm kiếm đa hãng)")
                    .to("direct:flightPartnerService")

                .when(simple("${body.serviceType} == 'FLIGHT_BOOKING'"))
                    .log("[HUB-CORE] => Chuyển tiếp sang Cổng Đặt & Giữ vé Hàng không (Flight Booking Gateway)")
                    .to("direct:flightBookingPartnerService")

                .when(simple("${body.serviceType} == 'SIM'"))
                    .log("[HUB-CORE] => Chuyển tiếp sang Cổng Đối tác SIM & eSIM (SIM Partner Gateway)")
                    .to("direct:simPartnerService")

                .when(simple("${body.serviceType} == 'INSURANCE'"))
                    .log("[HUB-CORE] => Chuyển tiếp sang Cổng Đối tác Bảo hiểm (Insurance Partner Gateway)")
                    .to("direct:insurancePartnerService")

                .when(simple("${body.serviceType} == 'ATTRACTION' || ${body.serviceType} == 'BILL'"))
                    .log("[HUB-CORE] => Chuyển tiếp sang Cổng Đối tác Vé vui chơi / Hóa đơn (Attraction Gateway)")
                    .to("direct:attractionPartnerService")

                .otherwise()
                    .log("[HUB-CORE] => Dịch vụ không xác định: ${body.serviceType}")
                    .to("direct:unknownService")
            .end()

            .log("[HUB-CORE] Hoàn tất điều phối cho [OrderId: ${exchangeProperty.OrderId}]");

        // Tuyến tương thích ngược cho direct:billPartnerService
        from("direct:billPartnerService")
            .routeId("hub-bill-backward-compat")
            .to("direct:attractionPartnerService");

        // Luồng từ chối khi gặp dịch vụ ngoài danh mục hỗ trợ của Hub
        from("direct:unknownService")
            .routeId("hub-rejected-service")
            .process(exchange -> {
                OrderRequest req = exchange.getMessage().getBody(OrderRequest.class);
                OrderResponse resp = new OrderResponse(
                    req != null ? req.getOrderId() : "N/A",
                    "REJECTED",
                    req != null ? req.getServiceType() : "UNKNOWN",
                    0.0,
                    "Hub chưa hỗ trợ loại dịch vụ này: " + (req != null ? req.getServiceType() : "NULL"),
                    "HUB_SERVICE_REGISTRY"
                );
                exchange.getMessage().setBody(resp);
            });

        // Luồng tra cứu trạng thái đơn hàng (Inquiry / Check Trans)
        from("direct:queryOrderStatus")
            .routeId("hub-order-status-query")
            .log("[HUB-INQUIRY] Tra cứu trạng thái cho đơn hàng: ${body}")
            .process(exchange -> {
                String orderId = exchange.getMessage().getBody(String.class);
                OrderResponse resp = new OrderResponse(
                    orderId != null ? orderId : "N/A",
                    "SUCCESS",
                    "QUERY_STATUS",
                    0.0,
                    "Đơn hàng " + orderId + " đã được ghi nhận và xử lý thành công trên hệ thống Hub",
                    "HUB_ORDER_LEDGER"
                );
                exchange.getMessage().setBody(resp);
            });
    }
}
