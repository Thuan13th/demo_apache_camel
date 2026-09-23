package com.demo_apache_camel.partner.telco.adapter;

import com.demo_apache_camel.hub.model.OrderRequest;
import com.demo_apache_camel.hub.model.OrderResponse;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

/**
 * [TELCO ADAPTER - NAPAS FALLBACK]
 * Cổng dự phòng Napas khi nhà mạng chính sập kết nối.
 */
@Component
public class NapasFallbackRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        from("direct:napasFallbackGateway")
            .routeId("adapter-napas-fallback")
            .process(exchange -> {
                OrderRequest req = exchange.getProperty("OriginalRequest", OrderRequest.class);
                OrderResponse fallbackResp = new OrderResponse(
                    req != null ? req.getOrderId() : "N/A",
                    "FALLBACK_SUCCESS",
                    "TOPUP",
                    req != null ? req.getAmount() : 0.0,
                    "Nhà mạng chính gián đoạn. Đã tự động chuyển tuyến qua cổng dự phòng Napas.",
                    "NAPAS_FALLBACK_GATEWAY"
                );
                exchange.getMessage().setBody(fallbackResp);
            });
    }
}
