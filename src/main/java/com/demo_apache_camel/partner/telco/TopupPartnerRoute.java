package com.demo_apache_camel.partner.telco;

import com.demo_apache_camel.hub.model.OrderRequest;
import com.demo_apache_camel.hub.model.OrderResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

/**
 * [PHÂN HỆ VIỄN THÔNG - TELCO PARTNER ORCHESTRATOR]
 * Tuyến điều phối trung tâm cho dịch vụ Topup/Viễn thông:
 * 1. Idempotent Consumer: Chống nạp thẻ trùng lặp qua kho hubIdempotentRepository.
 * 2. Content-Based Router: Phân luồng sang các Adapter chuyên biệt (Viettel, Mobi, Vina).
 * 3. Error Handling & Retry/Fallback: Thử lại khi nhà mạng lỗi và tự động chuyển tuyến Napas.
 */
@Slf4j
@Component
public class TopupPartnerRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {

        // 1. Quản trị lỗi & Fallback: Khi nhà mạng gián đoạn -> Thử lại 2 lần -> Fallback Napas
        onException(IllegalStateException.class)
            .maximumRedeliveries(2)
            .redeliveryDelay(500)
            .retryAttemptedLogLevel(LoggingLevel.WARN)
            .handled(true)
            .to("direct:napasFallbackGateway");

        // 2. Tuyến điều phối chính
        from("direct:topupPartnerService")
            .routeId("telco-partner-orchestrator")
            .setProperty("OriginalRequest", body())

            // EIP: Chống nạp thẻ trùng lặp (Idempotent Consumer)
            .idempotentConsumer(simple("${body.orderId}"))
                .idempotentRepository("hubIdempotentRepository")
                .skipDuplicate(false)
            .filter(exchangeProperty(Exchange.DUPLICATE_MESSAGE).isEqualTo(true))
                .process(exchange -> {
                    OrderRequest req = exchange.getProperty("OriginalRequest", OrderRequest.class);
                    OrderResponse dup = new OrderResponse(
                        req != null ? req.getOrderId() : "N/A",
                        "REJECTED",
                        "TOPUP",
                        0.0,
                        "Giao dịch nạp thẻ đã được xử lý trước đó (Chặn trùng lặp Idempotency)",
                        "HUB_IDEMPOTENCY_GUARD"
                    );
                    exchange.getMessage().setBody(dup);
                })
                .stop()
            .end()

            // EIP: Content-Based Router chuyển tiếp sang các Adapter
            .choice()
                .when(simple("${body.provider} == 'VIETTEL'")).to("direct:callViettelTelco")
                .when(simple("${body.provider} == 'MOBI'")).to("direct:callMobiTelco")
                .when(simple("${body.provider} == 'VINA'")).to("direct:callVinaTelco")
                .otherwise()
                    .process(exchange -> {
                        OrderRequest req = exchange.getMessage().getBody(OrderRequest.class);
                        exchange.getMessage().setBody(new OrderResponse(
                            req.getOrderId(), "FAILED", "TOPUP", req.getAmount(),
                            "Nhà mạng viễn thông không hỗ trợ: " + req.getProvider(), "TELCO_VALIDATOR"
                        ));
                    })
            .end();
    }
}
