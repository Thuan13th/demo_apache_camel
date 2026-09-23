package com.demo_apache_camel.partner.attraction;

import com.demo_apache_camel.hub.model.OrderRequest;
import com.demo_apache_camel.hub.model.OrderResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

/**
 * [PHÂN HỆ VÉ VUI CHƠI & DỊCH VỤ - ATTRACTION PARTNER ORCHESTRATOR]
 * Tuyến điều phối nghiệp vụ mua vé tham quan, vui chơi giải trí & thanh toán dịch vụ:
 * Phân luồng sang VinWonders, SunWorld hoặc dịch vụ hóa đơn tiện ích (EVN, Nước).
 */
@Slf4j
@Component
public class AttractionPartnerRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {

        from("direct:attractionPartnerService")
            .routeId("attraction-partner-orchestrator")
            .log("[ATTRACTION-HUB] Tiếp nhận yêu cầu mua vé / dịch vụ [Đối tác: ${body.provider}]")
            .choice()
                .when(simple("${body.provider} == 'VINWONDERS'"))
                    .to("direct:callVinwonders")
                .when(simple("${body.provider} == 'SUNWORLD'"))
                    .to("direct:callSunworld")
                .otherwise()
                    // Mặc định hỗ trợ thanh toán hóa đơn / dịch vụ chung (EVN, v.v.)
                    .process(exchange -> {
                        OrderRequest req = exchange.getMessage().getBody(OrderRequest.class);
                        exchange.getMessage().setBody(new OrderResponse(
                            req != null ? req.getOrderId() : "N/A",
                            "SUCCESS",
                            req != null ? req.getServiceType() : "BILL",
                            req != null ? req.getAmount() : 0.0,
                            "Thanh toán dịch vụ thành công cho đối tác: " + (req != null ? req.getProvider() : "BILL_GATEWAY"),
                            req != null && req.getProvider() != null ? req.getProvider() : "BILL_GATEWAY"
                        ));
                    })
            .end();
    }
}
