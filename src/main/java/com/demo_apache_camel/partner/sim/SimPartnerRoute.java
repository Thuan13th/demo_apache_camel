package com.demo_apache_camel.partner.sim;

import com.demo_apache_camel.hub.model.OrderRequest;
import com.demo_apache_camel.hub.model.OrderResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

/**
 * [PHÂN HỆ SIM & ESIM - SIM PARTNER ORCHESTRATOR]
 * Tuyến điều phối nghiệp vụ mua SIM & kích hoạt eSIM:
 * Phân luồng sang các nhà mạng (Wintel, Viettel) hoặc từ chối nếu không hỗ trợ.
 */
@Slf4j
@Component
public class SimPartnerRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {

        from("direct:simPartnerService")
            .routeId("sim-partner-orchestrator")
            .log("[SIM-HUB] Tiếp nhận yêu cầu SIM [Nhà mạng: ${body.provider}]")
            .choice()
                .when(simple("${body.provider} == 'WINTEL'"))
                    .to("direct:callWintelEsim")
                .when(simple("${body.provider} == 'VIETTEL'"))
                    .to("direct:callViettelSim")
                .otherwise()
                    .process(exchange -> {
                        OrderRequest req = exchange.getMessage().getBody(OrderRequest.class);
                        exchange.getMessage().setBody(new OrderResponse(
                            req != null ? req.getOrderId() : "N/A",
                            "FAILED",
                            "SIM",
                            0.0,
                            "Nhà mạng cung cấp SIM chưa được hỗ trợ: " + (req != null ? req.getProvider() : "NULL"),
                            "SIM_VALIDATOR"
                        ));
                    })
            .end();
    }
}
