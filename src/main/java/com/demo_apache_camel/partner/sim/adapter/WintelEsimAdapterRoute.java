package com.demo_apache_camel.partner.sim.adapter;

import com.demo_apache_camel.hub.model.OrderRequest;
import com.demo_apache_camel.hub.model.OrderResponse;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * [SIM ADAPTER - WINTEL ESIM]
 * Cấp phát mã QR Code kích hoạt eSIM Wintel (Gói cước không giới hạn Data).
 */
@Component
public class WintelEsimAdapterRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        from("direct:callWintelEsim")
            .routeId("adapter-wintel-esim")
            .process(exchange -> {
                OrderRequest req = exchange.getMessage().getBody(OrderRequest.class);
                String qrCode = "LPA:1$wintel.vn$" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
                exchange.getMessage().setBody(new OrderResponse(
                    req != null ? req.getOrderId() : "N/A",
                    "SUCCESS",
                    "SIM",
                    req != null && req.getAmount() != null ? req.getAmount() : 120000.0,
                    String.format("Cấp phát eSIM Wintel thành công! Mã QR kích hoạt: %s", qrCode),
                    "WINTEL_ESIM_GATEWAY"
                ));
            });
    }
}
