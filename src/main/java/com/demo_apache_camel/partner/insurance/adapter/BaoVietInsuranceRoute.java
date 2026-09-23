package com.demo_apache_camel.partner.insurance.adapter;

import com.demo_apache_camel.hub.model.OrderRequest;
import com.demo_apache_camel.hub.model.OrderResponse;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * [INSURANCE ADAPTER - BẢO VIỆT]
 * Adapter phát hành hợp đồng bảo hiểm du lịch / sức khỏe Bảo Việt.
 */
@Component
public class BaoVietInsuranceRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        from("direct:callBaoVietInsurance")
            .routeId("adapter-baoviet-insurance")
            .process(exchange -> {
                OrderRequest req = exchange.getMessage().getBody(OrderRequest.class);
                String policyNumber = "BV-POLICY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                exchange.getMessage().setBody(new OrderResponse(
                    req != null ? req.getOrderId() : "N/A",
                    "SUCCESS",
                    "INSURANCE",
                    req != null && req.getAmount() != null ? req.getAmount() : 250000.0,
                    String.format("Phát hành chứng nhận bảo hiểm Bảo Việt thành công! Số HĐ: %s (Người thụ hưởng: %s)", 
                            policyNumber, req != null ? req.getCustomerName() : "Khách hàng"),
                    "BAOVIET_INSURANCE_GATEWAY"
                ));
            });
    }
}
