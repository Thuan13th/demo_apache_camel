package com.demo_apache_camel.partner.common;

import com.demo_apache_camel.hub.config.PartnerOAuth2Properties;
import com.demo_apache_camel.hub.model.OrderRequest;
import com.demo_apache_camel.hub.model.OrderResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

/**
 * [CONFIG-DRIVEN DYNAMIC ROUTE]
 * Tuyến định tuyến động hướng cấu hình (Configuration-Driven Routing):
 * Thay vì viết if-else hardcode hoặc tạo riêng từng class Adapter Java cho mỗi đối tác,
 * tuyến này đọc trực tiếp danh sách đối tác được khai báo trong application.yaml
 * (hub.partner-oauth2.partners) để tự động điều phối, trích xuất cấu hình và xử lý.
 *
 * Khi doanh nghiệp ký kết với đối tác mới, CHỈ CẦN THÊM CẤU HÌNH TRONG YAML,
 * KHÔNG CẦN VIẾT THÊM DÒNG CODE JAVA NÀO!
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConfigDrivenDynamicRoute extends RouteBuilder {

    private final PartnerOAuth2Properties partnerOAuth2Properties;

    @Override
    public void configure() throws Exception {

        from("direct:configDrivenDispatch")
            .routeId("hub-config-driven-dynamic-dispatcher")
            .log("[DYNAMIC-ROUTER] Tiếp nhận yêu cầu điều phối động theo cấu hình YAML: Provider=${body.provider}")

            .process(exchange -> {
                OrderRequest req = exchange.getMessage().getBody(OrderRequest.class);
                String provider = (req != null && req.getProvider() != null)
                        ? req.getProvider().toUpperCase()
                        : "UNKNOWN";

                // Kiểm tra xem đối tác đã được khai báo trong application.yaml (hub.partner-oauth2.partners) chưa
                PartnerOAuth2Properties.PartnerCredentials creds =
                        partnerOAuth2Properties.getPartners().get(provider);

                if (creds != null) {
                    log.info("[DYNAMIC-ROUTER] Tìm thấy cấu hình đối tác '{}' trong application.yaml: URL={}, Scope={}",
                            provider, creds.getTokenUrl(), creds.getScope());

                    // Trả về OrderResponse thành công được dựng hoàn toàn từ thông số cấu hình YAML
                    OrderResponse resp = new OrderResponse(
                        req.getOrderId(),
                        "SUCCESS",
                        req.getServiceType() != null ? req.getServiceType() : "DYNAMIC",
                        req.getAmount() != null ? req.getAmount() : 0.0,
                        String.format("Điều phối động thành công tới đối tác '%s' cấu hình tại %s [Scope: %s]",
                                provider, creds.getTokenUrl(), creds.getScope()),
                        "DYNAMIC_CONFIG_ROUTER_" + provider
                    );
                    exchange.getMessage().setBody(resp);
                } else {
                    log.warn("[DYNAMIC-ROUTER] Đối tác '{}' chưa được khai báo trong application.yaml!", provider);
                    OrderResponse resp = new OrderResponse(
                        req != null ? req.getOrderId() : "N/A",
                        "REJECTED",
                        "DYNAMIC",
                        0.0,
                        String.format("Đối tác '%s' chưa được khai báo trong cấu hình application.yaml", provider),
                        "DYNAMIC_CONFIG_GUARD"
                    );
                    exchange.getMessage().setBody(resp);
                }
            });
    }
}
