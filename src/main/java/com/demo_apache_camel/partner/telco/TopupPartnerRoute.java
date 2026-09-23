package com.demo_apache_camel.partner.telco;

import com.demo_apache_camel.hub.model.OrderRequest;
import com.demo_apache_camel.hub.model.OrderResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * [PHÂN HỆ VIỄN THÔNG - TELCO PARTNER INTEGRATION]
 * Tinh gọn & tập trung vào các mẫu thiết kế cốt lõi của Camel:
 * 1. Idempotent Consumer: Chống nạp thẻ trùng lặp qua kho hubIdempotentRepository.
 * 2. Content-Based Router: Phân luồng Viettel, Mobifone, Vinaphone.
 * 3. Error Handling & Retry/Fallback: Tự động thử lại khi Vinaphone lỗi và chuyển tuyến Napas.
 */
@Slf4j
@Component
public class TopupPartnerRoute extends RouteBuilder {

    private final AtomicInteger failCounter = new AtomicInteger(0);

    @Override
    public void configure() throws Exception {

        // 1. Quản trị lỗi & Fallback: Khi nhà mạng gián đoạn -> Thử lại 2 lần -> Fallback Napas
        onException(IllegalStateException.class)
            .maximumRedeliveries(2)
            .redeliveryDelay(500)
            .retryAttemptedLogLevel(LoggingLevel.WARN)
            .handled(true)
            .process(exchange -> {
                OrderRequest req = exchange.getProperty("OriginalRequest", OrderRequest.class);
                OrderResponse fallbackResp = new OrderResponse(
                    req != null ? req.getOrderId() : "N/A",
                    "FALLBACK_SUCCESS",
                    "TOPUP",
                    req != null ? req.getAmount() : 0.0,
                    "Nhà mạng chính gián đoạn. Đã tự động chuyển tuyến qua cổng dự phòng Napas.",
                    "NAPAS_FALLBACK_GATEWAY"
                );
                exchange.getMessage().setBody(fallbackResp);
            });

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

            // EIP: Content-Based Router
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

        // 3. Adapter các nhà mạng
        from("direct:callViettelTelco")
            .routeId("adapter-viettel-telco")
            .process(exchange -> {
                OrderRequest req = exchange.getMessage().getBody(OrderRequest.class);
                exchange.getMessage().setBody(new OrderResponse(
                    req.getOrderId(), "SUCCESS", "TOPUP", req.getAmount() * 0.95,
                    "Nạp thẻ Viettel thành công (Chiết khấu 5%)", "VIETTEL_TELCO_DIRECT"
                ));
            });

        from("direct:callMobiTelco")
            .routeId("adapter-mobi-telco")
            .process(exchange -> {
                OrderRequest req = exchange.getMessage().getBody(OrderRequest.class);
                exchange.getMessage().setBody(new OrderResponse(
                    req.getOrderId(), "SUCCESS", "TOPUP", req.getAmount() * 0.97,
                    "Nạp thẻ Mobifone thành công (Chiết khấu 3%)", "MOBIFONE_TELCO_DIRECT"
                ));
            });

        from("direct:callVinaTelco")
            .routeId("adapter-vina-telco")
            .process(exchange -> {
                OrderRequest req = exchange.getMessage().getBody(OrderRequest.class);
                boolean isError = "true".equalsIgnoreCase(exchange.getIn().getHeader("X-Simulate-Error", String.class))
                        || (req.getAmount() != null && req.getAmount() == 999999.0);

                if (isError) {
                    int count = failCounter.incrementAndGet();
                    log.warn("[ADAPTER-VINA] Kênh truyền Vinaphone gián đoạn (Lần thử {})! Đang kích hoạt Camel Retry...", count);
                    throw new IllegalStateException("Kênh truyền Vinaphone 503 Service Unavailable");
                }
                exchange.getMessage().setBody(new OrderResponse(
                    req.getOrderId(), "SUCCESS", "TOPUP", req.getAmount() * 0.96,
                    "Nạp thẻ Vinaphone thành công (Chiết khấu 4%)", "VINAPHONE_TELCO_DIRECT"
                ));
            });
    }
}
