package com.demo_apache_camel.partner.telco.adapter;

import com.demo_apache_camel.hub.model.OrderRequest;
import com.demo_apache_camel.hub.model.OrderResponse;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

/**
 * [TELCO ADAPTER - VIETTEL]
 * Adapter xử lý nạp thẻ Viettel trực tiếp với mức chiết khấu 5%.
 */
@Component
public class ViettelTelcoRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        from("direct:callViettelTelco")
            .routeId("adapter-viettel-telco")
            .process(exchange -> {
                OrderRequest req = exchange.getMessage().getBody(OrderRequest.class);
                exchange.getMessage().setBody(new OrderResponse(
                    req.getOrderId(),
                    "SUCCESS",
                    "TOPUP",
                    req.getAmount() != null ? req.getAmount() * 0.95 : 0.0,
                    "Nạp thẻ Viettel thành công (Chiết khấu 5%)",
                    "VIETTEL_TELCO_DIRECT"
                ));
            });
    }
}
