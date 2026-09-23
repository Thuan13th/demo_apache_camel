package com.demo_apache_camel.partner.insurance;

import com.demo_apache_camel.hub.model.OrderRequest;
import com.demo_apache_camel.hub.model.OrderResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

/**
 * [PHÂN HỆ BẢO HIỂM - INSURANCE PARTNER ORCHESTRATOR]
 * Tuyến điều phối nghiệp vụ mua bảo hiểm trực tuyến:
 * Phân luồng tính phí và cấp đơn sang Bảo Việt hoặc PVI.
 */
@Slf4j
@Component
public class InsurancePartnerRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {

        from("direct:insurancePartnerService")
            .routeId("insurance-partner-orchestrator")
            .log("[INSURANCE-HUB] Tiếp nhận yêu cầu mua bảo hiểm [Nhà cung cấp: ${body.provider}]")
            .choice()
                .when(simple("${body.provider} == 'BAOVIET'"))
                    .to("direct:callBaoVietInsurance")
                .when(simple("${body.provider} == 'PVI'"))
                    .to("direct:callPviInsurance")
                .otherwise()
                    .process(exchange -> {
                        OrderRequest req = exchange.getMessage().getBody(OrderRequest.class);
                        exchange.getMessage().setBody(new OrderResponse(
                            req != null ? req.getOrderId() : "N/A",
                            "FAILED",
                            "INSURANCE",
                            0.0,
                            "Nhà cung cấp bảo hiểm chưa được hỗ trợ: " + (req != null ? req.getProvider() : "NULL"),
                            "INSURANCE_VALIDATOR"
                        ));
                    })
            .end();
    }
}
