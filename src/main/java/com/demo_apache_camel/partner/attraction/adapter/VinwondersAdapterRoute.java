package com.demo_apache_camel.partner.attraction.adapter;

import com.demo_apache_camel.hub.model.OrderRequest;
import com.demo_apache_camel.hub.model.OrderResponse;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * [ATTRACTION ADAPTER - VINWONDERS]
 * Adapter xuất vé điện tử và cấp mã QR vào cổng VinWonders / Vinpearl Safari.
 */
@Component
public class VinwondersAdapterRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        from("direct:callVinwonders")
            .routeId("adapter-vinwonders")
            .process(exchange -> {
                OrderRequest req = exchange.getMessage().getBody(OrderRequest.class);
                String ticketQr = "VW-TICKET-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                exchange.getMessage().setBody(new OrderResponse(
                    req != null ? req.getOrderId() : "N/A",
                    "SUCCESS",
                    "ATTRACTION",
                    req != null && req.getAmount() != null ? req.getAmount() : 850000.0,
                    String.format("Xuất vé VinWonders thành công! Mã QR vào cổng: %s", ticketQr),
                    "VINWONDERS_GATEWAY"
                ));
            });
    }
}
