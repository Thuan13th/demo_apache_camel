package com.demo_apache_camel.partner.sim.adapter;

import com.demo_apache_camel.hub.model.OrderRequest;
import com.demo_apache_camel.hub.model.OrderResponse;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * [SIM ADAPTER - VIETTEL SIM]
 * Đấu nối SIM số đẹp / SIM Data 4G Viettel và kích hoạt gói cước.
 */
@Component
public class ViettelSimAdapterRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        from("direct:callViettelSim")
            .routeId("adapter-viettel-sim")
            .process(exchange -> {
                OrderRequest req = exchange.getMessage().getBody(OrderRequest.class);
                String serialSim = "898404" + UUID.randomUUID().toString().replaceAll("[^0-9]", "").substring(0, 10);
                exchange.getMessage().setBody(new OrderResponse(
                    req != null ? req.getOrderId() : "N/A",
                    "SUCCESS",
                    "SIM",
                    req != null && req.getAmount() != null ? req.getAmount() : 150000.0,
                    String.format("Đấu nối SIM Viettel Data thành công! Số Serial SIM: %s", serialSim),
                    "VIETTEL_SIM_DIRECT"
                ));
            });
    }
}
