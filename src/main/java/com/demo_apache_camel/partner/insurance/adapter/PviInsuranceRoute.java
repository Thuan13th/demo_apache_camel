package com.demo_apache_camel.partner.insurance.adapter;

import com.demo_apache_camel.hub.model.OrderRequest;
import com.demo_apache_camel.hub.model.OrderResponse;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * [INSURANCE ADAPTER - PVI]
 * Adapter phát hành hợp đồng bảo hiểm tai nạn / xe cơ giới PVI.
 */
@Component
public class PviInsuranceRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        from("direct:callPviInsurance")
            .routeId("adapter-pvi-insurance")
            .process(exchange -> {
                OrderRequest req = exchange.getMessage().getBody(OrderRequest.class);
                String policyNumber = "PVI-CERT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                exchange.getMessage().setBody(new OrderResponse(
                    req != null ? req.getOrderId() : "N/A",
                    "SUCCESS",
                    "INSURANCE",
                    req != null && req.getAmount() != null ? req.getAmount() : 300000.0,
                    String.format("Phát hành chứng nhận bảo hiểm PVI thành công! Số HĐ: %s", policyNumber),
                    "PVI_INSURANCE_GATEWAY"
                ));
            });
    }
}
