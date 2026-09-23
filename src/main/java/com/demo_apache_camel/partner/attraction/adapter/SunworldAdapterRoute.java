package com.demo_apache_camel.partner.attraction.adapter;

import com.demo_apache_camel.hub.model.OrderRequest;
import com.demo_apache_camel.hub.model.OrderResponse;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * [ATTRACTION ADAPTER - SUNWORLD]
 * Adapter xuất vé cáp treo & công viên giải trí Sun World (Bà Nà Hills, Fansipan).
 */
@Component
public class SunworldAdapterRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        from("direct:callSunworld")
            .routeId("adapter-sunworld")
            .process(exchange -> {
                OrderRequest req = exchange.getMessage().getBody(OrderRequest.class);
                String ticketQr = "SW-TICKET-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                exchange.getMessage().setBody(new OrderResponse(
                    req != null ? req.getOrderId() : "N/A",
                    "SUCCESS",
                    "ATTRACTION",
                    req != null && req.getAmount() != null ? req.getAmount() : 900000.0,
                    String.format("Xuất vé Sun World thành công! Mã vé điện tử: %s", ticketQr),
                    "SUNWORLD_GATEWAY"
                ));
            });
    }
}
