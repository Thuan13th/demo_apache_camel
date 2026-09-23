package com.demo_apache_camel.partner.telco.adapter;

import com.demo_apache_camel.hub.model.OrderRequest;
import com.demo_apache_camel.hub.model.OrderResponse;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

/**
 * [TELCO ADAPTER - MOBIFONE]
 * Adapter xử lý nạp thẻ Mobifone với mức chiết khấu 3%.
 */
@Component
public class MobifoneTelcoRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        from("direct:callMobiTelco")
            .routeId("adapter-mobi-telco")
            .process(exchange -> {
                OrderRequest req = exchange.getMessage().getBody(OrderRequest.class);
                exchange.getMessage().setBody(new OrderResponse(
                    req.getOrderId(),
                    "SUCCESS",
                    "TOPUP",
                    req.getAmount() != null ? req.getAmount() * 0.97 : 0.0,
                    "Nạp thẻ Mobifone thành công (Chiết khấu 3%)",
                    "MOBIFONE_TELCO_DIRECT"
                ));
            });
    }
}
